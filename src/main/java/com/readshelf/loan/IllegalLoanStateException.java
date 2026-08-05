package com.readshelf.loan;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Thrown when a loan transition is attempted from the wrong state (e.g. approving a loan
 * that isn't REQUESTED). The @ControllerAdvice maps this to 409 Conflict. Reused by all
 * three transition endpoints; carries the loan, its current state, and the states allowed.
 *
 * `required` is a SET, not a single status: some transitions accept more than one starting
 * state — returning is legal from ACTIVE *or* OVERDUE, since a book in your hands is
 * returnable whether or not it's late. Naming only one of them makes the 409 body actively
 * misleading, telling a caller "must be ACTIVE" when OVERDUE would also have been accepted.
 *
 * Varargs rather than an explicit Set so the single-state call sites read exactly as before,
 * and the message keeps its original wording when there's only one option.
 */
public class IllegalLoanStateException extends RuntimeException {

    private final UUID loanId;
    private final LoanStatus current;
    private final Set<LoanStatus> required;

    public IllegalLoanStateException(UUID loanId, LoanStatus current, LoanStatus... required) {
        super(buildMessage(loanId, current, required));
        this.loanId = loanId;
        this.current = current;
        // EnumSet iterates in declaration order, so the message lists states in lifecycle
        // order rather than whatever order the caller happened to pass them in.
        this.required = toSet(required);
    }

    private static Set<LoanStatus> toSet(LoanStatus... required) {
        if (required.length == 0) {
            throw new IllegalArgumentException("At least one required state must be given");
        }
        return EnumSet.of(required[0], required);
    }

    private static String buildMessage(UUID loanId, LoanStatus current, LoanStatus... required) {
        Set<LoanStatus> allowed = toSet(required);
        if (allowed.size() == 1) {
            return String.format("Loan %s is in state %s but must be %s for this action",
                    loanId, current, allowed.iterator().next());
        }
        String joined = allowed.stream().map(Enum::name).collect(Collectors.joining(", "));
        return String.format("Loan %s is in state %s but must be one of [%s] for this action",
                loanId, current, joined);
    }
}