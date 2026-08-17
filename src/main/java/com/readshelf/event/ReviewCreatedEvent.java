package com.readshelf.event;

import java.util.UUID;

/**
 * Wire shape of "review.created": a user reviewed a work.
 *
 * Carries bookId as well as reviewId, which looks redundant next to this project's "events carry
 * ids, consumers re-read" rule — it isn't. The search index denormalizes review text INTO the
 * book document, so the thing that goes stale is the BOOK. bookId is the identity of the affected
 * document, not denormalized content, and it's what all three review events have in common.
 *
 * Why this is its own type rather than one lumped REVIEW_CHANGED: an event names what happened,
 * not what a consumer should do about it. The indexer treats created/updated/deleted identically
 * (rebuild the document from Postgres), but that's a fact about the indexer, so it's expressed in
 * the indexer — three handlers calling one reindex method. A future consumer, like Phase 16's
 * webhooks, genuinely needs to tell a new review apart from a deleted one.
 */
public record ReviewCreatedEvent(UUID reviewId, UUID bookId) {
}
