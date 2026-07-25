package com.readshelf.user;

import java.util.UUID;

/**
 * Thrown when a user's profile is looked up and doesn't exist. HTTP-agnostic
 * (the @ControllerAdvice maps it to 404) — same Phase 8 contract as BookNotFoundException.
 */
public class UserProfileNotFoundException extends RuntimeException {

    private final UUID userId;

    public UserProfileNotFoundException(UUID userId) {
        super(String.format("Profile for user %s not found", userId));
        this.userId = userId;
    }
}