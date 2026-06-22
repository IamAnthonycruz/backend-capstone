package com.readshelf.book;

import com.readshelf.utils.PagedResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/book-copies")
public class BookCopyController {

    private final BookCopyService bookCopyService;

    public BookCopyController(BookCopyService bookCopyService) {
        this.bookCopyService = bookCopyService;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<BookCopyResponseDTO>> listBookCopies(
            @Min(0) @RequestParam(defaultValue = "0") int page,
            @Min(1) @Max(100) @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "CREATED_AT") BookCopySortField sortBy
    ) {
        return ResponseEntity.ok(bookCopyService.findAll(page, size, sortBy));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookCopyResponseDTO> getBookCopy(@PathVariable UUID id) {
        return bookCopyService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<BookCopyResponseDTO> createBookCopy(@Valid @RequestBody BookCopyRequestDTO request) {
        BookCopyResponseDTO created = bookCopyService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BookCopyResponseDTO> updateBookCopy(@PathVariable UUID id,
                                                             @Valid @RequestBody BookCopyRequestDTO request) {
        return bookCopyService.update(id, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBookCopy(@PathVariable UUID id) {
        return bookCopyService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}