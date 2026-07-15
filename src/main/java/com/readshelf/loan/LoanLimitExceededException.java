package com.readshelf.loan;

import java.util.UUID;

/**
 * Thrown when a borrower already holds the maximum number of active loans and
 * tries to request another. The @ControllerAdvice maps this to 409 Conflict.
 *
 * HTTP-agnostic; carries the borrower and the limit so the message stays truthful
 * even if MAX_ACTIVE_LOANS changes.
 */
public class LoanLimitExceededException extends RuntimeException {

    private final UUID borrowerId;
    private final int max;

    public LoanLimitExceededException(UUID borrowerId, int max) {
        super(String.format("Borrower %s already has the maximum of %d active loans", borrowerId, max));
        this.borrowerId = borrowerId;
        this.max = max;
    }
}
