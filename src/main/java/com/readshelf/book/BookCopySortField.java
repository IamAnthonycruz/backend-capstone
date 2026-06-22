package com.readshelf.book;

/**
 * Whitelisted sort fields for the book-copy list endpoint. Spring binds ?sortBy to this
 * enum; an unknown value is rejected at binding time with a 400. Each constant maps
 * to the entity property name used in Sort.by(...).
 */
public enum BookCopySortField {
    CREATED_AT("createdAt"),
    IS_AVAILABLE("isAvailable");

    private final String property;

    BookCopySortField(String property) {
        this.property = property;
    }

    public String getProperty() {
        return property;
    }
}
