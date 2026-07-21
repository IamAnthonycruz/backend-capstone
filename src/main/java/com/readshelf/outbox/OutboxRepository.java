package com.readshelf.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    // The poller's "find work to do" query: unpublished rows, oldest first so events
    // ship in the order they happened. Derived query — Spring Data builds the SQL from
    // the method name (processedAt IS NULL ORDER BY createdAt ASC).
    List<OutboxEvent> findByProcessedAtIsNullOrderByCreatedAtAsc();
}