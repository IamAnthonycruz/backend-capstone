package com.readshelf.event;

import java.util.UUID;

/**
 * Wire shape of the "loan.requested" event. Must stay in sync with the payload map built in
 * LoanService.create() — that map is what actually gets serialized into the outbox row.
 *
 * Deliberately carries IDs, not embedded objects: an event is a fact about something that
 * happened, and a consumer that needs details should look them up against the current state
 * rather than trust a snapshot that may be minutes stale by the time it's delivered.
 */
public record LoanRequestedEvent(UUID loanId, UUID lenderId, UUID borrowerId) {
}
