package com.readshelf.book;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// References the catalog Book and the owning User by id (service resolves them).
// isAvailable is optional on create (defaults true); settable on update.
public record BookCopyRequestDTO(
        @NotNull UUID bookId,
        @NotNull UUID ownerId,
        Boolean isAvailable
) {
}