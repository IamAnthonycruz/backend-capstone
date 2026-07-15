package com.readshelf.loan;

import java.util.UUID;

/**
 * Thrown when the caller is neither the lender nor the borrower entitled to perform
 * a loan transition (approve/pickup/return). The @ControllerAdvice maps this to 403.
 *
 * HTTP-agnostic; carries the loan being acted on and the attempted action so the
 * message is specific about what was refused.
 */
public class UnauthorizedLoanActionException extends RuntimeException {

    private final UUID loanId;
    private final String action;

    public UnauthorizedLoanActionException(UUID loanId, String action) {
        super(String.format("You are not allowed to %s loan %s", action, loanId));
        this.loanId = loanId;
        this.action = action;
    }
}
