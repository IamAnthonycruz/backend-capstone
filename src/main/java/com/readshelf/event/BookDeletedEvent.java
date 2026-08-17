package com.readshelf.event;

import java.util.UUID;

/**
 * Wire shape of the "book.deleted" event: a work was removed from the catalog.
 *
 * Phase 11 built every other book producer and deliberately skipped this one, because the only
 * thing that would consume it is removal from a search index, and the index didn't exist yet.
 * Publishing an event nobody can bind to means RabbitMQ silently discards it — a producer
 * written against a consumer nobody has built. Phase 12 adds the event, the queue, the handler
 * and the EVENT_TYPE_MAPPING entry together.
 *
 * Still just the id — but for a stronger reason than the other book events. The consumer of
 * book.created re-reads the book from Postgres; the consumer of THIS event cannot, because the
 * row is gone by the time the message is delivered. The id is all that's left, and it's all that
 * is needed: deleting a document only requires knowing which one.
 */
public record BookDeletedEvent(UUID bookId) {
}
