package com.readshelf.book;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * v2 of the books API. Separate controller (rather than overloading the v1 one) so the
 * URI version maps cleanly to its own class. Only the richer detail endpoint lives here;
 * v1 remains the home for the rest until they need a v2 shape too.
 */
@RestController
@RequestMapping("/api/v2/books")
public class BookV2Controller {

    private final BookService bookService;

    public BookV2Controller(BookService bookService) {
        this.bookService = bookService;
    }

    // Richer book detail: v1 fields + averageRating + reviewCount.
    @GetMapping("/{id}")
    public ResponseEntity<BookDetailV2DTO> getBookDetail(@PathVariable UUID id) {
        return bookService.findDetailById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}