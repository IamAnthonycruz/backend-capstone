package com.readshelf.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserRequestDTO(
        @NotBlank
        @Size(max = 255)
        String username,

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(min = 8)
        String password,

        // Restrict to the values the DB CHECK allows, so a bad role is a clean 400
        // rather than an ugly DB constraint violation.
        // ⚠️ DECISION FLAG: letting a client set role means a client could self-assign ADMIN.
        // Acceptable for Phase 2 CRUD; Phase 5 (auth) should lock this down (default BORROWER,
        // only admins can elevate).
        @NotBlank
        @Pattern(regexp = "BORROWER|LENDER|ADMIN", message = "role must be BORROWER, LENDER, or ADMIN")
        String role
) {
}