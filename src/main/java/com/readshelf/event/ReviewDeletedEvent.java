package com.readshelf.event;

import java.util.UUID;

/**
 * Wire shape of "review.deleted": a review was removed.
 *
 * This is the event that FORCES bookId into the payload of all three. By the time this message is
 * delivered the review row is gone, so a consumer holding only a reviewId has no way to learn
 * which book document needs rebuilding — the join it would need was deleted along with the row.
 * The other two events carry bookId for consistency; this one carries it out of necessity.
 */
public record ReviewDeletedEvent(UUID reviewId, UUID bookId) {
}
