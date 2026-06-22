package com.readshelf.review;

/**
 * Whitelisted sort fields for the review list endpoint. Spring binds ?sortBy to this
 * enum; an unknown value is rejected at binding time with a 400. Each constant maps
 * to the entity property name used in Sort.by(...).
 */
public enum ReviewSortField {
    RATING("rating"),
    CREATED_AT("createdAt");

    private final String property;

    ReviewSortField(String property) {
        this.property = property;
    }

    public String getProperty() {
        return property;
    }
}
