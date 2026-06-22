package com.readshelf.book;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.ISBN;


public record BookRequestDTO(
        @NotBlank
        @ISBN
        String isbn,
        @NotBlank
        String title,
        @NotBlank
        String author,
        String genre,
        @Size(max = 200)
        String summary) {
}
