package com.readshelf.review;

import com.readshelf.book.Book;
import com.readshelf.book.BookRepository;
import com.readshelf.loan.LoanRepository;
import com.readshelf.user.User;
import com.readshelf.user.UserRepository;
import com.readshelf.utils.PagedResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    public ReviewService(ReviewRepository reviewRepository,
                         UserRepository userRepository,
                         BookRepository bookRepository,
                         LoanRepository loanRepository,
                         ReviewMapper reviewMapper) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
        this.reviewMapper = reviewMapper;
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
        try {
            reviewRepository.saveAndFlush(review);
        }catch(DataIntegrityViolationException dataIntegrityViolationException){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A review by this user for this book already exists");
        }
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
        reviewRepository.save(review);
        return Optional.of(reviewMapper.toResponseDTO(review));
    }

    public boolean delete(UUID id) {
        if (!reviewRepository.existsById(id)) {
            return false;
        }
        reviewRepository.deleteById(id);
        return true;
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