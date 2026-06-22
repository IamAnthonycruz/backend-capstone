package com.readshelf.wishlist;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WishlistRequestDTO(
        @NotNull UUID userId,
        @NotNull UUID bookId
) {
}