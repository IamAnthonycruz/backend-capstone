package com.readshelf.auth;

/**
 * Structured auth response: the short-lived stateless access token plus the
 * long-lived stateful refresh token. Returned by login and by /auth/refresh.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken
) {
}