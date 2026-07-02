package com.readshelf.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Login payload. No format constraints beyond "present" — we don't reveal password
 * rules at login, and a wrong credential is a uniform 401 regardless of shape.
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}