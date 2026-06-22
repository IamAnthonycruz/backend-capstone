package com.readshelf.review;

import java.util.UUID;

// Expose the related entities as ids (not nested objects) — keeps the response flat
// and dodges lazy-loading/recursion issues.
public record ReviewResponseDTO(UUID id, UUID userId, UUID bookId, int rating, String content) {
}