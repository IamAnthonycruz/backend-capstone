package com.readshelf.user;

/**
 * Whitelisted sort fields for the user list endpoint. Spring binds ?sortBy to this
 * enum; an unknown value is rejected at binding time with a 400. Each constant maps
 * to the entity property name used in Sort.by(...).
 */
public enum UserSortField {
    USERNAME("username"),
    EMAIL("email"),
    CREATED_AT("createdAt");

    private final String property;

    UserSortField(String property) {
        this.property = property;
    }

    public String getProperty() {
        return property;
    }
}
