package com.readshelf.book;

import com.readshelf.utils.PagedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Business/data layer for books. The controller stays HTTP-only and delegates here.
 *
 * Convention: service methods take/return DTOs (they own the BookMapper + repository).
 *
 * Note for later: write methods (create/update/delete) are where @Transactional will go
 * in Phase 7 — leaving it off for now to avoid pre-building concurrency concerns.
 */
@Service
public class BookService {

    private final BookRepository bookRepository;
    private final BookMapper bookMapper;

    public BookService(BookRepository bookRepository, BookMapper bookMapper) {
        this.bookRepository = bookRepository;
        this.bookMapper = bookMapper;
    }

    public BookResponseDTO getById(UUID id) {
        return bookRepository.findById(id)
                .map(bookMapper::toResponseDTO)
                .orElseThrow(() -> new BookNotFoundException(id));
    }

    // v2 detail: the query builds the DTO directly (no mapper) — aggregates can't come
    // from a single entity mapping.
    public Optional<BookDetailV2DTO> findDetailById(UUID id) {
        return bookRepository.findBookDetailById(id);
    }

    public BookResponseDTO create(BookRequestDTO request) {
        Book saved = bookRepository.save(bookMapper.toEntity(request));
        return bookMapper.toResponseDTO(saved);
    }

    public PagedResponse<BookResponseDTO> findAll(int page, int size, BookSortField sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy.getProperty()).and(Sort.by("id")).ascending());
        return PagedResponse.from(bookRepository.findAll(pageable).map(bookMapper::toResponseDTO));
    }

    public BookResponseDTO update(UUID id, BookRequestDTO request) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new BookNotFoundException(id));
        book.setSummary(request.summary());
        book.setIsbn(request.isbn());
        book.setTitle(request.title());
        book.setAuthor(request.author());
        book.setGenre(request.genre());
        bookRepository.save(book);

        return bookMapper.toResponseDTO(book);
    }

    public void delete(UUID id) {
        if (!bookRepository.existsById(id)) {
            throw new BookNotFoundException(id);
        }
        bookRepository.deleteById(id);
    }
}