package com.readshelf.loan;

import com.readshelf.book.BookCopy;
import com.readshelf.book.BookCopyRepository;
import com.readshelf.user.User;
import com.readshelf.user.UserRepository;
import com.readshelf.utils.CursorPage;
import com.readshelf.utils.PagedResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Three FKs to resolve (lender, borrower, book copy). No generic update(): a loan's
 * lifecycle is a state machine (REQUESTED -> APPROVED -> ACTIVE -> RETURNED/OVERDUE),
 * built as dedicated transition endpoints in Phase 7 — not a blunt PUT.
 *
 * Deferred to Phase 7: lender != borrower check; @Transactional; optimistic locking.
 */
@Service
public class LoanService {

    private final LoanRepository loanRepository;
    private final UserRepository userRepository;
    private final BookCopyRepository bookCopyRepository;
    private final LoanMapper loanMapper;

    public LoanService(LoanRepository loanRepository,
                       UserRepository userRepository,
                       BookCopyRepository bookCopyRepository,
                       LoanMapper loanMapper) {
        this.loanRepository = loanRepository;
        this.userRepository = userRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.loanMapper = loanMapper;
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

        Loan loan = loanMapper.toEntity(request);
        loan.setLender(lender);
        loan.setBorrower(borrower);
        loan.setBookCopy(bookCopy);
        loan.setStatus(LoanStatus.REQUESTED); // every loan starts as a request
        return loanMapper.toResponseDTO(loanRepository.save(loan));
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