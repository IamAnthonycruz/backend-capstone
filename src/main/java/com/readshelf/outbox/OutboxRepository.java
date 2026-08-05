package com.readshelf.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    // The poller's "find work to do" query: unpublished rows, oldest first so events
    // ship in the order they happened. Derived query — Spring Data builds the SQL from
    // the method name (processedAt IS NULL ORDER BY createdAt ASC).
    List<OutboxEvent> findByProcessedAtIsNullOrderByCreatedAtAsc();

    /**
     * Deletes at most batchSize already-published rows older than the cutoff, returning how many
     * it actually removed. The caller loops until a batch comes back short.
     *
     * `processed_at IS NOT NULL` is load-bearing, not decoration: an OLD row that was never
     * published is a stuck event, not garbage. Something failed and nobody noticed. Deleting it
     * here would destroy the only evidence and silently drop the event forever, so this query
     * cannot see those rows at all.
     *
     * The LIMIT lives in a subquery because SQL has no `DELETE ... LIMIT` in standard form
     * (Postgres in particular rejects it). Deleting the whole backlog in one statement would
     * hold row locks and grow the table's dead tuples for the entire transaction; capped batches
     * in separate transactions keep each lock window short.
     *
     * Native rather than JPQL: bulk delete is a set operation with no entities to load, and
     * JPQL has no LIMIT clause to build the subquery with.
     */
    @Modifying
    @Query(value = """
            DELETE FROM outbox
            WHERE id IN (
                SELECT id FROM outbox
                WHERE processed_at IS NOT NULL
                  AND processed_at < :cutoff
                ORDER BY processed_at
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteProcessedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}