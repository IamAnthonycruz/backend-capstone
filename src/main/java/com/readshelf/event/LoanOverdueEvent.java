package com.readshelf.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape of the "loan.overdue" event: this loan BECAME overdue.
 *
 * Past tense, and it means it — this fires exactly once per loan, on the ACTIVE -> OVERDUE
 * transition. It is NOT a nightly reminder. The scheduled checker only selects ACTIVE loans, so
 * flipping the status is what stops the same loan from re-emitting tomorrow: the state machine
 * enforces emit-once, no dedup bookkeeping required.
 *
 * That property is load-bearing for idempotency. If this fired every night, a consumer could no
 * longer tell a DUPLICATE DELIVERY (at-least-once doing its thing) from a legitimate second
 * night — and messageId would stop being a usable dedup key.
 *
 * dueDate rides along because it IS the fact being reported, not a convenience for one consumer
 * (contrast lenderEmail, which was deliberately kept off LoanRequestedEvent).
 */
public record LoanOverdueEvent(UUID loanId, UUID lenderId, UUID borrowerId, Instant dueDate) {
}
