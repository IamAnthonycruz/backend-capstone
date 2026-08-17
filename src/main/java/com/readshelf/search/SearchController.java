package com.readshelf.search;

import com.readshelf.utils.PagedResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Search lives at its own root rather than under /api/v1/books, because it isn't a view of the
 * books resource — it's a query across a separate store with its own consistency guarantees and
 * its own failure mode. /api/v1/search/reviews or /search/users can join it here later.
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final BookSearchService bookSearchService;

    public SearchController(BookSearchService bookSearchService) {
        this.bookSearchService = bookSearchService;
    }

    /**
     * @param q the raw user query. @NotBlank rather than @NotNull: an empty box should be a 400,
     *          not a match-everything query that quietly returns the whole catalog.
     */
    @GetMapping("/books")
    public ResponseEntity<PagedResponse<SearchResultDTO>> searchBooks(
            @NotBlank @RequestParam String q,
            @Min(0) @RequestParam(defaultValue = "0") int page,
            @Min(1) @Max(100) @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(bookSearchService.search(q, page, size));
    }
}
