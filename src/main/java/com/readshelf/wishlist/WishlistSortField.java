package com.readshelf.wishlist;

/**
 * Whitelisted sort fields for the wishlist list endpoint. Spring binds ?sortBy to this
 * enum; an unknown value is rejected at binding time with a 400. Each constant maps
 * to the entity property name used in Sort.by(...). A wishlist entry only has
 * created_at as a meaningful scalar (the rest are identity FKs).
 */
public enum WishlistSortField {
    CREATED_AT("createdAt");

    private final String property;

    WishlistSortField(String property) {
        this.property = property;
    }

    public String getProperty() {
        return property;
    }
}
