package com.readshelf.user;

import java.util.UUID;

// NOTE: password is deliberately ABSENT — never expose a credential in a response.
public record UserResponseDTO(UUID id, String username, String email, String role) {
}