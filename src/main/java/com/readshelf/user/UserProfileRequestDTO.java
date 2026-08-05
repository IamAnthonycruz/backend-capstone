package com.readshelf.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound payload for creating/updating a profile. The owning user is taken from the
 * URL ({@code /users/{id}/profile}), NOT the body — you can't set someone else's profile
 * by putting a different userId in the JSON.
 */
public record UserProfileRequestDTO(
        @NotBlank @Size(max = 255) String displayName,
        @Size(max = 3000) String bio,
        @Size(max = 255) String location,
        @Size(max = 2048) String profilePictureUrl
) {
}