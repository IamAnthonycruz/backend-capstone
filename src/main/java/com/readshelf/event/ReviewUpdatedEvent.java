package com.readshelf.event;

import java.util.UUID;

/**
 * Wire shape of "review.updated": a user edited their review's rating or content.
 *
 * The reason this event has to exist at all: if only creation were published, editing a review to
 * remove a phrase would leave that phrase in the search index forever. A search would then return
 * a book for text that no longer exists anywhere — worse than missing data, because it looks
 * authoritative. See ReviewCreatedEvent for why bookId rides along.
 */
public record ReviewUpdatedEvent(UUID reviewId, UUID bookId) {
}
