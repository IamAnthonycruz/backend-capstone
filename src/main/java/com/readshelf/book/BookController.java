package com.readshelf.book;

import com.readshelf.review.ReviewResponseDTO;
import com.readshelf.review.ReviewService;
import com.readshelf.review.ReviewSortField;
import com.readshelf.utils.PagedResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

import java.util.UUID;


@RestController
@RequestMapping("/api/v1/books")
public class BookController {

    private final BookService bookService;
    private final ReviewService reviewService;

    public BookController(BookService bookService, ReviewService reviewService) {
        this.bookService = bookService;
        this.reviewService = reviewService;
    }

    // ----- Worked examples: controller does HTTP, service does the work -----

    @GetMapping("/{id}")
    public ResponseEntity<BookResponseDTO> getBook(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        // Missing book -> BookNotFoundException -> 404 ProblemDetail (handled by the advice).
        BookResponseDTO book = bookService.getById(id);

        // Strong ETag derived from the last-modified version. Changes on every update.
        String etag = "\"" + book.updatedAt().toEpochMilli() + "\"";

        if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(book);

    }

    @PostMapping
    public ResponseEntity<BookResponseDTO> createBook(@Valid @RequestBody BookRequestDTO request) {
        BookResponseDTO created = bookService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<BookResponseDTO>> listBooks(
            @Min(0)
            @RequestParam(defaultValue = "0") int page,
            @Min(1) @Max(100)
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "TITLE") BookSortField sortBy
    ) {
        return ResponseEntity.ok().body(bookService.findAll(page, size, sortBy));
    }


    // Nested route: reviews rooted at their book. 404 if the book doesn't exist
    // (ReviewService.findByBook does that check), else the book's page of reviews.
    @GetMapping("/{bookId}/reviews")
    public ResponseEntity<PagedResponse<ReviewResponseDTO>> listBookReviews(
            @PathVariable UUID bookId,
            @Min(0) @RequestParam(defaultValue = "0") int page,
            @Min(1) @Max(100) @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "CREATED_AT") ReviewSortField sortBy
    ) {
        return ResponseEntity.ok(reviewService.findByBook(bookId, page, size, sortBy));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BookResponseDTO> updateBook(@PathVariable UUID id,
                                                      @Valid @RequestBody BookRequestDTO request) {
        return ResponseEntity.ok(bookService.update(id, request));
    }

    // TODO(human): only admins may delete any book. Add a method-level @PreAuthorize
    // here. This is a pure ROLE check (the word "any" — owner doesn't matter), so it can
    // be decided before the method runs. Hint: hasRole('...') matches the ROLE_<x>
    // authority your JwtAuthFilter set, so pass the role name WITHOUT the ROLE_ prefix.
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable UUID id) {
        // Missing book -> BookNotFoundException -> 404 ProblemDetail (handled by the advice).
        bookService.delete(id);
        return ResponseEntity.noContent().build();
    }
}