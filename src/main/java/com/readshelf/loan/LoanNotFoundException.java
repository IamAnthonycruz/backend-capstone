package com.readshelf.loan;

import java.util.UUID;

/**
 * Thrown when a loan is looked up by id and doesn't exist. Mirrors BookNotFoundException;
 * the @ControllerAdvice maps this to 404. Reused by all three transition endpoints.
 */
public class LoanNotFoundException extends RuntimeException {

    private final UUID id;

    public LoanNotFoundException(UUID id) {
        super(String.format("Loan with id %s not found", id));
        this.id = id;
    }
}
