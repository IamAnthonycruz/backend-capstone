package com.readshelf.book;

import java.util.UUID;

/**
 * v2 book detail: v1's fields plus aggregates over the book's reviews.
 * Built directly by a JPQL constructor expression (SELECT new ...), so the parameter
 * ORDER and TYPES here must line up exactly with the query's SELECT list.
 * averageRating is a nullable Double — a book with zero reviews yields AVG = null.
 */
public record BookDetailV2DTO(
        UUID id,
        String isbn,
        String title,
        String author,
        String genre,
        String summary,
        Double averageRating,
        Long reviewCount
) {
}