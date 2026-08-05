package com.readshelf.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape of the "loan.approved" event: the lender accepted this borrow request.
 *
 * IDs only, plus the timestamp that IS the fact being reported. No email address, no book
 * title — a consumer that needs those looks them up against current state, so one consumer's
 * needs never leak into the producer. See LoanRequestedEvent for the full reasoning.
 */
public record LoanApprovedEvent(UUID loanId, UUID lenderId, UUID borrowerId, Instant approvedAt) {
}