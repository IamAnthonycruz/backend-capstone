package com.readshelf.book;

import com.readshelf.user.User;
import com.readshelf.user.UserRepository;
import com.readshelf.utils.PagedResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Service
public class BookCopyService {

    private final BookCopyRepository bookCopyRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final BookCopyMapper bookCopyMapper;

    public BookCopyService(BookCopyRepository bookCopyRepository,
                           BookRepository bookRepository,
                           UserRepository userRepository,
                           BookCopyMapper bookCopyMapper) {
        this.bookCopyRepository = bookCopyRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.bookCopyMapper = bookCopyMapper;
    }

    public PagedResponse<BookCopyResponseDTO> findAll(int page, int size, BookCopySortField sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy.getProperty()).and(Sort.by("id")).ascending());
        return PagedResponse.from(bookCopyRepository.findAll(pageable).map(bookCopyMapper::toResponseDTO));
    }

    public Optional<BookCopyResponseDTO> findById(UUID id) {
        return bookCopyRepository.findById(id).map(bookCopyMapper::toResponseDTO);
    }

    public BookCopyResponseDTO create(BookCopyRequestDTO request) {
        Book book = resolveBook(request.bookId());
        User owner = resolveOwner(request.ownerId());

        BookCopy copy = bookCopyMapper.toEntity(request);
        copy.setBook(book);
        copy.setOwner(owner);
        return bookCopyMapper.toResponseDTO(bookCopyRepository.save(copy));
    }

    public Optional<BookCopyResponseDTO> update(UUID id, BookCopyRequestDTO request) {
        Optional<BookCopy> existing = bookCopyRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        // book + owner are identity; availability is the mutable field.
        BookCopy copy = existing.get();
        if (request.isAvailable() != null) {
            copy.setAvailable(request.isAvailable());
        }
        bookCopyRepository.save(copy);
        return Optional.of(bookCopyMapper.toResponseDTO(copy));
    }

    public boolean delete(UUID id) {
        if (!bookCopyRepository.existsById(id)) {
            return false;
        }
        bookCopyRepository.deleteById(id);
        return true;
    }

    private Book resolveBook(UUID bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "No book with id " + bookId));
    }

    private User resolveOwner(UUID ownerId) {
        return userRepository.findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "No user with id " + ownerId));
    }
}