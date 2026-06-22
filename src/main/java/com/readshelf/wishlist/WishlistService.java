package com.readshelf.wishlist;

import com.readshelf.book.Book;
import com.readshelf.book.BookRepository;
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
 * No update(): a wishlist entry has nothing mutable (just user+book+created_at, all
 * identity). It's create / read / delete only.
 */
@Service
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final WishlistMapper wishlistMapper;

    public WishlistService(WishlistRepository wishlistRepository,
                           UserRepository userRepository,
                           BookRepository bookRepository,
                           WishlistMapper wishlistMapper) {
        this.wishlistRepository = wishlistRepository;
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.wishlistMapper = wishlistMapper;
    }

    public PagedResponse<WishlistResponseDTO> findAll(int page, int size, WishlistSortField sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy.getProperty()).and(Sort.by("id")).ascending());
        return PagedResponse.from(wishlistRepository.findAll(pageable).map(wishlistMapper::toResponseDTO));
    }

    public Optional<WishlistResponseDTO> findById(UUID id) {
        return wishlistRepository.findById(id).map(wishlistMapper::toResponseDTO);
    }

    public WishlistResponseDTO create(WishlistRequestDTO request) {
        User user = resolveUser(request.userId());
        Book book = resolveBook(request.bookId());

        Wishlist wishlist = wishlistMapper.toEntity(request);
        wishlist.setUser(user);
        wishlist.setBook(book);
        try {
            wishlistRepository.saveAndFlush(wishlist);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This book is already on the user's wishlist");
        }
        return wishlistMapper.toResponseDTO(wishlist);
    }

    public boolean delete(UUID id) {
        if (!wishlistRepository.existsById(id)) {
            return false;
        }
        wishlistRepository.deleteById(id);
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