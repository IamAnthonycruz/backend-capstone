package com.readshelf.wishlist;

import com.readshelf.utils.EntityMapper;
import org.springframework.stereotype.Component;

@Component
public class WishlistMapper implements EntityMapper<WishlistRequestDTO, WishlistResponseDTO, Wishlist> {

    // No scalars to set — a wishlist row is just (user, book, created_at). The service
    // attaches the resolved user + book; created_at is @CreationTimestamp.
    @Override
    public Wishlist toEntity(WishlistRequestDTO request) {
        return new Wishlist();
    }

    @Override
    public WishlistResponseDTO toResponseDTO(Wishlist wishlist) {
        return new WishlistResponseDTO(
                wishlist.getId(),
                wishlist.getUser().getId(),
                wishlist.getBook().getId(),
                wishlist.getCreatedAt()
        );
    }
}