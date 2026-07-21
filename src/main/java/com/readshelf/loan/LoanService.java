package com.readshelf.loan;

import com.readshelf.book.BookCopy;
import com.readshelf.book.BookCopyRepository;
import com.readshelf.outbox.OutboxEvent;
import com.readshelf.outbox.OutboxRepository;
import com.readshelf.user.User;
import com.readshelf.user.UserRepository;
import com.readshelf.utils.CursorPage;
import com.readshelf.utils.PagedResponse;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Three FKs to resolve (lender, borrower, book copy). No generic update(): a loan's
 * lifecycle is a state machine (REQUESTED -> APPROVED -> ACTIVE -> RETURNED/OVERDUE),
 * built as dedicated transition endpoints (approve/pickup/return) — not a blunt PUT.
 *
 * Phase 7 business rules live here: create-time guards (own-copy, availability, max
 * active loans), state-transition authz/guards, @Transactional writes, and @Version
 * optimistic locking on Loan/BookCopy. lender != borrower is enforced upstream by
 * @NoSelfLoan on the request DTO.
 */
@Service
public class LoanService {
    private static final int MAX_ACTIVE_LOANS = 3;
    private final LoanRepository loanRepository;
    private final UserRepository userRepository;
    private final BookCopyRepository bookCopyRepository;
    private final LoanMapper loanMapper;
    // Programmatic transaction boundary for create(). Spring Boot auto-configures a
    // TransactionTemplate bean over the JPA transaction manager; we inject it so we can
    // scope the transaction to only the write, not the reads/guards above it.
    private final TransactionTemplate transactionTemplate;
    // Outbox collaborators: the repo persists the event row, the mapper serializes the
    // payload to a JSON string. Both used inside create()'s transaction.
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public LoanService(LoanRepository loanRepository,
                       UserRepository userRepository,
                       BookCopyRepository bookCopyRepository,
                       LoanMapper loanMapper,
                       TransactionTemplate transactionTemplate,
                       OutboxRepository outboxRepository,
                       ObjectMapper objectMapper) {
        this.loanRepository = loanRepository;
        this.userRepository = userRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.loanMapper = loanMapper;
        this.transactionTemplate = transactionTemplate;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public PagedResponse<LoanResponseDTO> findAll(int page, int size, LoanSortField sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy.getProperty()).and(Sort.by("id")).ascending());
        return PagedResponse.from(loanRepository.findAll(pageable).map(loanMapper::toResponseDTO));
    }

    public Optional<LoanResponseDTO> findById(UUID id) {
        return loanRepository.findById(id).map(loanMapper::toResponseDTO);
    }

    public CursorPage<LoanResponseDTO> findByCursor(String cursorToken, int limit) {
        List<Loan> loans;

        // 1. Determine if we are fetching the first page or a subsequent page
        if (cursorToken == null || cursorToken.isBlank()) {
            loans = loanRepository.findFirstPage(limit);
        } else {
            LoanCursor token = LoanCursor.decode(cursorToken);
            loans = loanRepository.findLoansAfterCursor(token.dueDate(), token.id(), limit);
        }

        // 2. Map domain entities to DTOs
        var loansResponse = loans.stream()
                .map(loanMapper::toResponseDTO)
                .toList();

        // 3. Generate the next cursor token if there might be a next page
        String nextCursorToken = null;
        if (loansResponse.size() == limit) {
            var lastLoan = loansResponse.getLast();
            LoanCursor loanCursor = new LoanCursor(lastLoan.dueDate(), lastLoan.id());
            nextCursorToken = loanCursor.encode(); // Capture the Base64 string here
        }


        // 4. Return the page with the encoded string token
        return new CursorPage<LoanResponseDTO>(loansResponse, nextCursorToken);
    }

    public LoanResponseDTO create(LoanRequestDTO request) {
        User lender = resolveUser(request.lenderId());
        User borrower = resolveUser(request.borrowerId());
        BookCopy bookCopy = resolveBookCopy(request.bookCopyId());

        // 1. Can't borrow a copy you own. One-off precondition (fires only here), so it
        //    stays an inline ResponseStatusException rather than a named domain exception —
        //    the advice still renders it as a ProblemDetail (403).
        if (bookCopy.getOwner().getId().equals(borrower.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot borrow a book copy that you own.");
        }

        // 2. Copy must be available -> 409
        if (!bookCopy.isAvailable()) {
            throw new BookAlreadyLentException(bookCopy.getId());
        }

        // 3. Max active loans per borrower -> 409
        var loanStates = Set.of(LoanStatus.ACTIVE, LoanStatus.APPROVED, LoanStatus.OVERDUE);
        long activeLoanCount = loanRepository.countByBorrower_IdAndStatusIn(borrower.getId(), loanStates);
        if (activeLoanCount >= MAX_ACTIVE_LOANS) {
            throw new LoanLimitExceededException(borrower.getId(), MAX_ACTIVE_LOANS);
        }

        // Everything above (FK resolution + the 3 guards) is reads/validation — it runs
        // OUTSIDE any transaction, so a failed guard throws before a tx is ever opened.

        // Map + build the loan (pure object work, no DB — fine outside the tx).
        Loan loan = loanMapper.toEntity(request);
        loan.setLender(lender);
        loan.setBorrower(borrower);
        loan.setBookCopy(bookCopy);
        loan.setStatus(LoanStatus.REQUESTED); // Every loan starts as a request

        // Only the write is transactional. The lambda IS the transaction: commits on
        // normal return, rolls back on throw. Both the loan AND its outbox event are
        // saved here, so they commit atomically — the whole point of the pattern.
        Loan saved = transactionTemplate.execute(status -> {
            Loan persisted = loanRepository.save(loan);
            // Using Java Map (Quickest)
            Map<String, Object> payloadMap = Map.of(
                    "loanId", persisted.getId(),
                    "lenderId", persisted.getLender().getId(),  // Adjust getter name to match your entity
                    "borrowerId", persisted.getBorrower().getId() // Adjust getter name to match your entity
            );

            var payloadJson = objectMapper.writeValueAsString(payloadMap);

            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setEventType("LOAN_REQUESTED");
            outboxEvent.setPayload(payloadJson);

            outboxRepository.save(outboxEvent);


            return persisted;
        });
        return loanMapper.toResponseDTO(saved);
    }

    /**
     * REQUESTED -> APPROVED. Only the loan's lender may approve, and only while it's REQUESTED.
     *
     * The 4-beat transition skeleton (same shape reused by pickup/return):
     *   1. LOAD    — fetch the loan by id, or 404 if it doesn't exist.
     *   2. AUTHZ   — is `callerId` the lender on this loan? If not -> 403.
     *   3. GUARD   — is the loan currently REQUESTED? If not -> 409 (wrong state).
     *   4. MUTATE  — set status APPROVED, stamp approvalDate, save, return the DTO.
     *
     * Throw ResponseStatusException(HttpStatus.X, msg) for each failure (404/403/409).
     */
    @Transactional
    public LoanResponseDTO approve(UUID loanId, UUID callerId) {
        Loan loan = loanRepository.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
        if (!callerId.equals(loan.getLender().getId())){
            throw new UnauthorizedLoanActionException(loanId, "approve");
        }
        if (loan.getStatus() != LoanStatus.REQUESTED) {
            throw new IllegalLoanStateException(loanId, loan.getStatus(), LoanStatus.REQUESTED);
        }
        if (!loan.getBookCopy().isAvailable()){
            throw new BookAlreadyLentException(loan.getBookCopy().getId());
        }
        loan.setStatus(LoanStatus.APPROVED);
        loan.setApprovalDate(Instant.now());
        loan.getBookCopy().setAvailable(false);
        return loanMapper.toResponseDTO(loanRepository.save(loan));
    }

    /**
     * APPROVED -> ACTIVE. Only the BORROWER may pick up, and only while it's APPROVED.
     * Same 4-beat skeleton as approve — but note two differences:
     *   - AUTHZ is against the borrower, not the lender.
     *   - The copy was already reserved (is_available=false) at approval, so pickup
     *     doesn't touch the copy at all. One entity changes -> think about whether it
     *     still needs @Transactional.
     */
    public LoanResponseDTO pickup(UUID loanId, UUID callerId) {
        Loan loan =  loanRepository.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
        if (!callerId.equals( loan.getBorrower().getId())) {
            throw new UnauthorizedLoanActionException(loanId, "pick up");
        }
        if(loan.getStatus() != LoanStatus.APPROVED){
            throw new IllegalLoanStateException(loanId, loan.getStatus(), LoanStatus.APPROVED);
        }
        loan.setStatus(LoanStatus.ACTIVE);
        loanRepository.saveAndFlush(loan);
        return loanMapper.toResponseDTO(loan);
    }

    /**
     * ACTIVE -> RETURNED. Only the BORROWER may return, and only while it's ACTIVE.
     * The mirror of approve's copy handling: this is where the copy becomes available
     * AGAIN. Two entities change (loan + copy) -> @Transactional, same reasoning as approve.
     * Also stamp returnDate.
     */
    @Transactional
    public LoanResponseDTO returnLoan(UUID loanId, UUID callerId) {
        Loan loan =   loanRepository.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
        if  (!callerId.equals(loan.getBorrower().getId())) {
            throw new UnauthorizedLoanActionException(loanId, "return");
        }
        if (loan.getStatus() != LoanStatus.ACTIVE){
            throw new IllegalLoanStateException(loanId, loan.getStatus(), LoanStatus.ACTIVE);
        }
        loan.setStatus(LoanStatus.RETURNED);
        loan.setReturnDate(Instant.now());
        loan.getBookCopy().setAvailable(true);
        loanRepository.saveAndFlush(loan);

        return loanMapper.toResponseDTO(loan);
    }

    public boolean delete(UUID id) {
        if (!loanRepository.existsById(id)) {
            return false;
        }
        loanRepository.deleteById(id);
        return true;
    }

    private User resolveUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "No user with id " + userId));
    }

    private BookCopy resolveBookCopy(UUID bookCopyId) {
        return bookCopyRepository.findById(bookCopyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "No book copy with id " + bookCopyId));
    }
}