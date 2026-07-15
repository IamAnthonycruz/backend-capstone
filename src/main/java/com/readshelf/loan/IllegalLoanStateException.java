package com.readshelf.loan;

import java.util.UUID;

/**
 * Thrown when a loan transition is attempted from the wrong state (e.g. approving a loan
 * that isn't REQUESTED). The @ControllerAdvice maps this to 409 Conflict. Reused by all
 * three transition endpoints; carries the loan, its current state, and the state required.
 */
public class IllegalLoanStateException extends RuntimeException {

    private final UUID loanId;
    private final LoanStatus current;
    private final LoanStatus required;

    public IllegalLoanStateException(UUID loanId, LoanStatus current, LoanStatus required) {
        super(String.format("Loan %s is in state %s but must be %s for this action", loanId, current, required));
        this.loanId = loanId;
        this.current = current;
        this.required = required;
    }
}
