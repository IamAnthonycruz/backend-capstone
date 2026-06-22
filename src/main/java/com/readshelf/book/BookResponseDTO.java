package com.readshelf.book;

import java.time.Instant;
import java.util.UUID;

public record BookResponseDTO(UUID id, String isbn, String title, String author, String genre, String summary,
                              Instant updatedAt) {
}
