package com.readshelf.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A durable record of a domain event, written in the SAME transaction as the business
 * change that produced it (see LoanService.create). A separate poller reads unprocessed
 * rows and publishes them, then stamps processedAt. This is the producer half of the
 * transactional outbox pattern — it turns the dual-write (DB + broker) into a single
 * atomic DB write.
 */
@Entity
@Table(name = "outbox")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // What kind of event this is (e.g. "LOAN_REQUESTED") — lets a consumer route and
    // deserialize without cracking open the payload.
    @Column(name = "event_type", nullable = false)
    private String eventType;

    // The serialized event body. @JdbcTypeCode(JSON) binds this String to the jsonb
    // column instead of a plain varchar.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // NULL until the poller publishes it; then the publish timestamp.
    @Column(name = "processed_at")
    private Instant processedAt;
}