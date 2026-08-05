package com.readshelf.event;

import java.util.UUID;

/**
 * Wire shape of the "book.updated" event: a catalog entry changed.
 *
 * No diff of what changed, and no new values — a consumer re-reads the book and takes whatever
 * it finds. That keeps this event correct under at-least-once delivery: re-applying "book X
 * changed, go look" twice is a no-op, whereas replaying a stale field-set could overwrite newer
 * data with older. Idempotency falls out of the event's shape rather than needing a dedup table.
 *
 * NOTE: nothing consumes this yet — see BookCreatedEvent.
 */
public record BookUpdatedEvent(UUID bookId) {
}
