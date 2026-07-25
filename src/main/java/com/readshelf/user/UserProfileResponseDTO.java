package com.readshelf.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound profile shape. Exposes the owner as an id (userId), consistent with the
 * repo convention of surfacing relations as ids rather than nested objects.
 */
public record UserProfileResponseDTO(
        UUID id,
        UUID userId,
        String displayName,
        String bio,
        String location,
        String profilePictureUrl,
        Instant createdAt,
        Instant updatedAt
) {
}