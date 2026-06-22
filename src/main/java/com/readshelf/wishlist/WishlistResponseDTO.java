package com.readshelf.wishlist;

import java.time.Instant;
import java.util.UUID;

public record WishlistResponseDTO(UUID id, UUID userId, UUID bookId, Instant createdAt) {
}