package com.readshelf.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for POST /auth/refresh: the client hands back the refresh token it holds
 * to exchange for a freshly rotated access + refresh pair.
 */
public record RefreshRequest(
        @NotBlank String refreshToken
) {
}
