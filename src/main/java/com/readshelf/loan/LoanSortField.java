package com.readshelf.loan;

/**
 * Whitelisted sort fields for the loan list endpoint. Spring binds ?sortBy to this
 * enum; an unknown value is rejected at binding time with a 400. Each constant maps
 * to the entity property name used in Sort.by(...).
 */
public enum LoanSortField {
    STATUS("status"),
    REQUEST_DATE("requestDate"),
    DUE_DATE("dueDate");

    private final String property;

    LoanSortField(String property) {
        this.property = property;
    }

    public String getProperty() {
        return property;
    }
}
