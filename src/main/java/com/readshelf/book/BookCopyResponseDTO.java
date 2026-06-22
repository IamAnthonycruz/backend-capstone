package com.readshelf.book;

import java.util.UUID;

public record BookCopyResponseDTO(UUID id, UUID bookId, UUID ownerId, boolean isAvailable) {
}