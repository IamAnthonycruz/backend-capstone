package com.readshelf.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// References User and Book by id; the service resolves them (fail-fast existence check).
public record ReviewRequestDTO(
        @NotNull UUID userId,
        @NotNull UUID bookId,
        @Min(1) @Max(5) int rating,
        @NotBlank @Size(max = 3000) String content
) {
}