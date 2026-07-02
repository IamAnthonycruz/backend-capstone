package com.readshelf.book;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.ISBN;


public record BookRequestDTO(
        @NotBlank
        @ISBN
        String isbn,
        @NotBlank
        @Size(max = 255)
        String title,
        @NotBlank
        @Size(max = 255)
        String author,
        String genre,
        @Size(max = 200)
        String summary) {
}
