package com.readshelf.book;

/**
 * Whitelisted sort fields for the book list endpoint. Spring binds the ?sortBy
 * query param straight to this enum — an unknown value is rejected at binding
 * time with a 400, so no value outside this set ever reaches the query.
 *
 * Each constant maps to the actual entity/column property name used in Sort.by(...).
 */
public enum BookSortField {
    TITLE("title"),
    AUTHOR("author"),
    GENRE("genre");

    private final String property;

    BookSortField(String property) {
        this.property = property;
    }

    public String getProperty(){
        return property;
    }
}
