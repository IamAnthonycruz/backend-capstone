package com.readshelf.review;

import com.readshelf.book.Book;
import com.readshelf.book.BookRepository;
import com.readshelf.event.ReviewCreatedEvent;
import com.readshelf.event.ReviewDeletedEvent;
import com.readshelf.event.ReviewUpdatedEvent;
import com.readshelf.loan.LoanRepository;
import com.readshelf.outbox.OutboxEvent;
import com.readshelf.outbox.OutboxRepository;
import com.readshelf.user.User;
import com.readshelf.user.UserRepository;
import com.readshelf.utils.PagedResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

/**
 * Like BookService, but with the NEW wrinkle: resolving foreign references.
 * On create, userId/bookId are validated up front (fail-fast) and the resolved
 * entities are attached before saving.
 */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;
    private final ReviewMapper reviewMapper;
    // Outbox collaborators, same trio as BookService: template scopes the transaction to the
    // write, repo persists the event row, mapper serializes the payload.
    private final TransactionTemplate transactionTemplate;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ReviewService(ReviewRepository reviewRepository,
                         UserRepository userRepository,
                         BookRepository bookRepository,
                         LoanRepository loanRepository,
                         ReviewMapper reviewMapper,
                         TransactionTemplate transactionTemplate,
                         OutboxRepository outboxRepository,
                         ObjectMapper objectMapper) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
        this.reviewMapper = reviewMapper;
        this.transactionTemplate = transactionTemplate;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public PagedResponse<ReviewResponseDTO> findAll(int page, int size, ReviewSortField sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy.getProperty()).and(Sort.by("id")).ascending());
        return PagedResponse.from(reviewRepository.findAll(pageable).map(reviewMapper::toResponseDTO));
    }

    public Optional<ReviewResponseDTO> findById(UUID id) {
        return reviewRepository.findById(id).map(reviewMapper::toResponseDTO);
    }

    public PagedResponse<ReviewResponseDTO> findByBook(UUID bookId, int page, int size, ReviewSortField sortBy) {
        if(!bookRepository.existsById(bookId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No book with id" + bookId );
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy.getProperty()).and(Sort.by("id")).ascending());
        return PagedResponse.from(reviewRepository.findByBook_Id(bookId, pageable).map(reviewMapper::toResponseDTO));
    }

    public ReviewResponseDTO create(ReviewRequestDTO request) {
        User user = resolveUser(request.userId());
        Book book = resolveBook(request.bookId());

        // You can only review a work you've actually borrowed (picked-up: see LoanRepository).
        if(!loanRepository.hasEverBorrowed(user.getId(), book.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot review a book you have not yet borrowed");
        }
        Review review = reviewMapper.toEntity(request);
        review.setUser(user);
        review.setBook(book);

        // Review + outbox row in ONE transaction, same reasoning as BookService: two separate
        // commits would let a crash between them persist the review while losing the event, and
        // the book document would then be missing this review's text until something else
        // happened to that book. The 409 path throws out of the callback, which rolls back BOTH.
        transactionTemplate.executeWithoutResult(status -> {
            try {
                reviewRepository.saveAndFlush(review);
            } catch (DataIntegrityViolationException dataIntegrityViolationException) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A review by this user for this book already exists");
            }
            publishOutbox("REVIEW_CREATED", "review.created",
                    new ReviewCreatedEvent(review.getId(), book.getId()));
        });

        return reviewMapper.toResponseDTO(review);
    }

    public Optional<ReviewResponseDTO> update(UUID id, ReviewRequestDTO request) {
        Optional<Review> existing = reviewRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        // A review's user+book are its identity — you don't move a review to another
        // book. Only rating/content are mutable here.
        Review review = existing.get();
        review.setRating(request.rating());
        review.setContent(request.content());

        transactionTemplate.executeWithoutResult(status -> {
            reviewRepository.save(review);
            publishOutbox("REVIEW_UPDATED", "review.updated",
                    new ReviewUpdatedEvent(review.getId(), review.getBook().getId()));
        });

        return Optional.of(reviewMapper.toResponseDTO(review));
    }

    /**
     * Loads the review rather than using existsById, because the event needs the owning book's id
     * and after the delete there is nowhere left to read it from.
     */
    public boolean delete(UUID id) {
        Optional<Review> existing = reviewRepository.findById(id);
        if (existing.isEmpty()) {
            return false;
        }
        UUID bookId = existing.get().getBook().getId();

        transactionTemplate.executeWithoutResult(status -> {
            reviewRepository.deleteById(id);
            publishOutbox("REVIEW_DELETED", "review.deleted", new ReviewDeletedEvent(id, bookId));
        });

        return true;
    }

    /**
     * Writes an outbox row. Must be called from inside a transaction that also contains the
     * business write — on its own it gives you nothing the pattern is for.
     */
    private void publishOutbox(String eventType, String routingKey, Object payload) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setEventType(eventType);
        outboxEvent.setRoutingKey(routingKey);
        outboxEvent.setPayload(objectMapper.writeValueAsString(payload));
        outboxRepository.save(outboxEvent);
    }

    private User resolveUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "No user with id " + userId));
    }

    private Book resolveBook(UUID bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "No book with id " + bookId));
    }
}