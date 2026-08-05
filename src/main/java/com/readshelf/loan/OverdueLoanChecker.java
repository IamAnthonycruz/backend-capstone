package com.readshelf.loan;

import com.readshelf.event.LoanOverdueEvent;
import com.readshelf.outbox.OutboxEvent;
import com.readshelf.outbox.OutboxRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

/**
 * Nightly sweep that moves past-due ACTIVE loans to OVERDUE and announces each transition.
 *
 * The emit-once property comes from the state machine, not from bookkeeping: the query only
 * selects ACTIVE loans, so once a loan is flipped to OVERDUE it can never be picked up again.
 * That is what lets LoanOverdueEvent be honestly past tense. This is NOT a recurring reminder —
 * a loan overdue for a week produces exactly one event, on the night it crossed the line.
 *
 * Note the SINGLE-INSTANCE assumption. Run two copies of the app and both wake at 02:00 and sweep
 * the same rows. @Version on Loan means the loser of a race fails rather than double-writing, but
 * a real deployment wants a distributed lock (ShedLock) or leader election. Out of scope here.
 */
@Component
public class OverdueLoanChecker {
    private static final Logger log = LoggerFactory.getLogger(OverdueLoanChecker.class);

    private final LoanRepository loanRepository;
    private final OutboxRepository outboxRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public OverdueLoanChecker(LoanRepository loanRepository,
                              OutboxRepository outboxRepository,
                              TransactionTemplate transactionTemplate,
                              ObjectMapper objectMapper) {
        this.loanRepository = loanRepository;
        this.outboxRepository = outboxRepository;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 02:00 daily by default. Spring's cron takes SIX fields — second minute hour day-of-month
     * month day-of-week — so the leading 0 is seconds, not minutes. Standard Unix cron has five.
     *
     * Externalized so the interval can be tightened for local testing without editing code
     * (waiting until 2am to find out whether the sweep works is not a debugging strategy).
     */
    @Scheduled(cron = "${readshelf.jobs.overdue-check-cron}")
    public void flagOverdueLoans() {
        var overdueLoans = loanRepository.findByStatusAndDueDateBefore(LoanStatus.ACTIVE, Instant.now());
        if (overdueLoans.isEmpty()) {
            log.debug("Overdue sweep: nothing past due");
            return;
        }

        int flagged = 0;
        int failed = 0;

        for (var loan : overdueLoans) {
            try {
                // ONE TRANSACTION PER LOAN, not one around the sweep: a single poison row would
                // otherwise roll back the whole batch and — if it fails deterministically — block
                // every other overdue loan again the next night, and the night after that. Same
                // reasoning as dead-lettering one bad message instead of stalling the queue.
                transactionTemplate.executeWithoutResult(status -> {
                    LoanOverdueEvent payload = new LoanOverdueEvent(
                            loan.getId(),
                            loan.getLender().getId(),
                            loan.getBorrower().getId(),
                            loan.getDueDate()
                    );
                    loan.setStatus(LoanStatus.OVERDUE);
                    loanRepository.save(loan);
                    OutboxEvent outboxEvent = new OutboxEvent();
                    outboxEvent.setEventType("LOAN_OVERDUE");
                    outboxEvent.setRoutingKey("loan.overdue");
                    outboxEvent.setPayload(objectMapper.writeValueAsString(payload));
                    outboxRepository.save(outboxEvent);
                });
                flagged++;
            }
            // Catching Exception is deliberate: executeWithoutResult rolls this loan back and
            // RETHROWS, which without a catch here would escape the for-loop and abandon every
            // remaining loan. A nightly job that silently stops halfway is worse than one that
            // records a bad row and carries on. The loan stays ACTIVE, so tomorrow's sweep
            // retries it — the emit-once property survives a failure.
            catch (Exception e) {
                failed++;
                // Loan id + due date are what you actually need at 9am to reproduce this; the
                // stack trace goes last so SLF4J logs it rather than treating it as a parameter.
                log.error("Overdue sweep failed for loan {} (due {})", loan.getId(), loan.getDueDate(), e);
            }
        }

        // Summary line, because "500 flagged" and "0 flagged, 500 failed" must not look alike in
        // the logs. Escalates to warn when anything failed so it's greppable.
        if (failed > 0) {
            log.warn("Overdue sweep finished: {} flagged, {} failed", flagged, failed);
        } else {
            log.info("Overdue sweep finished: {} flagged", flagged);
        }
    }
}
