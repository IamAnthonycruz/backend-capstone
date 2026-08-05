package com.readshelf.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

/**
 * Nightly cleanup for the outbox table. Nothing else deletes from it — the poller only stamps
 * processedAt — so without this the table grows forever, and the poller's "find work to do"
 * query scans an ever-larger table to find the same handful of unprocessed rows.
 *
 * RETENTION: published rows are kept for a week, then dropped. They're safe to delete the moment
 * they're published, but keeping them briefly answers a question the loans table can't: "did we
 * ever emit an event for this?" Without the row, a missing notification is ambiguous — the event
 * might never have been written, or might have been written, sent, and cleaned up. Those are
 * different bugs with different fixes. A week comfortably covers "someone noticed and asked."
 *
 * It also keeps the rows around long enough to be republished by hand if a consumer bug ate a
 * batch, though that's the rarer use in practice.
 */
@Component
public class OutboxCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupJob.class);

    // Rows per transaction. Large enough that a normal night is one or two round trips, small
    // enough that no single transaction holds locks for long.
    private static final int BATCH_SIZE = 500;

    // Safety valve: caps one run at 50k rows so a first run against a huge backlog can't churn
    // all night. Whatever it doesn't reach is still older than the cutoff tomorrow.
    private static final int MAX_BATCHES = 100;

    private final OutboxRepository outboxRepository;
    private final TransactionTemplate transactionTemplate;
    private final Duration retention;

    public OutboxCleanupJob(OutboxRepository outboxRepository,
                            TransactionTemplate transactionTemplate,
                            @Value("${readshelf.jobs.outbox-retention}") Duration retention) {
        this.outboxRepository = outboxRepository;
        this.transactionTemplate = transactionTemplate;
        this.retention = retention;
    }

    @Scheduled(cron = "${readshelf.jobs.outbox-cleanup-cron}")
    public void deleteOldProcessedEvents() {
        // Computed ONCE, outside the loop. Recomputing per batch would let the cutoff drift
        // forward mid-run, so which rows qualify would depend on how long the run has taken.
        Instant cutoff = Instant.now().minus(retention);

        int totalDeleted = 0;
        int batches = 0;
        while (batches < MAX_BATCHES) {
            // One transaction PER BATCH, which is the entire reason for batching. A single
            // transaction around the whole loop would hold every row lock until the last batch
            // committed — the long-transaction problem the batching exists to avoid.
            int deleted = transactionTemplate.execute(status ->
                    outboxRepository.deleteProcessedBefore(cutoff, BATCH_SIZE));
            totalDeleted += deleted;
            batches++;

            // A short batch means the query ran out of qualifying rows, so there's no next one.
            if (deleted < BATCH_SIZE) {
                break;
            }
        }

        if (totalDeleted == 0) {
            log.debug("Outbox cleanup: nothing published before {}", cutoff);
        } else if (batches == MAX_BATCHES) {
            log.warn("Outbox cleanup hit the {}-batch cap after deleting {} rows published before"
                    + " {} — backlog remains, next run will continue", MAX_BATCHES, totalDeleted, cutoff);
        } else {
            log.info("Outbox cleanup deleted {} rows published before {}", totalDeleted, cutoff);
        }
    }
}
