package com.readshelf.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The consumer half of the transactional outbox. On a timer it drains unpublished
 * events from the outbox table, "publishes" each (just logs for now — the real broker
 * is Phase 11), and stamps processedAt so the next tick skips it.
 *
 * Why a separate poller instead of publishing inline in create(): the write side only
 * has to guarantee the event is DURABLY STORED with the loan (one atomic tx). Actually
 * shipping it to a broker — which can be slow or down — is decoupled to here, where a
 * failure just means the row stays unprocessed and gets retried next tick.
 */
@Component
public class OutboxPoller {
    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxRepository outboxRepository;

    public OutboxPoller(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    // fixedDelay = wait 5s AFTER the previous run finishes before starting the next
    // (vs fixedRate, which fires every 5s regardless and can overlap). fixedDelay keeps
    // ticks from stacking up if one run runs long.
    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        var pendingEvents = outboxRepository.findByProcessedAtIsNullOrderByCreatedAtAsc();
        for (var event : pendingEvents) {
            log.info("Publishing outbox event {}: {}", event.getEventType(), event.getPayload());
            event.setProcessedAt(Instant.now());
            outboxRepository.save(event);
        }
    }
}