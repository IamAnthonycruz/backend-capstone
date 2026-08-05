package com.readshelf.event;

import java.util.UUID;

/**
 * Wire shape of the "book.created" event: a new work was added to the catalog.
 *
 * Just the id, deliberately. The obvious temptation is to carry title/author/genre so the
 * search indexer doesn't need a lookup — that's exactly the fattening this project decided
 * against. The moment those fields ride along, BookService is asserting what a search index
 * wants, and a replayed event indexes whatever the title was at publish time rather than now.
 *
 * NOTE: nothing consumes this yet. readshelf.search.queue ("book.#") arrives with Phase 12, so
 * until then this key matches no binding and the broker silently discards it. That is expected.
 */
public record BookCreatedEvent(UUID bookId) {
}