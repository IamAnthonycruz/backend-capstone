package com.readshelf.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. Deliberately has NO {@code role} field — registration is
 * public and self-service, so everyone starts as BORROWER (set in AuthService).
 * Validation mirrors UserRequestDTO (minus role).
 */
public record RegisterRequest(
        @NotBlank
        @Size(max = 255)
        String username,

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(min = 8)
        String password
) {
}
