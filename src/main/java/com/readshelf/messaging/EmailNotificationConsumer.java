package com.readshelf.messaging;

import com.readshelf.config.RabbitConfig;
import com.readshelf.event.LoanApprovedEvent;
import com.readshelf.event.LoanOverdueEvent;
import com.readshelf.event.LoanRequestedEvent;
import com.readshelf.loan.LoanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Listens on the email queue, which is bound to "loan.#" — so every loan event lands here.
 *
 * FAILURE POLICY (see application.yml + RabbitConfig for the wiring):
 *   - Any exception that escapes the handler is retried in-memory, 4 attempts with exponential
 *     backoff. This is the path for TRANSIENT failures — the mail server is down right now but
 *     may not be in eight seconds.
 *   - AmqpRejectAndDontRequeueException marks a PERMANENT failure — nothing changes between
 *     attempt 1 and attempt 4, so retrying is pure waste. Note this exception does NOT skip
 *     retries on its own: the retry interceptor runs first and retries anything. What makes it
 *     dead-letter on attempt 1 is the retry predicate in RabbitConfig, which classifies it as
 *     non-retryable. The exception alone only controls the DESTINATION (DLX, not requeue).
 *   - Either way, `default-requeue-rejected: false` sends the final rejection to the DLX
 *     rather than back onto this queue, so a bad message can't spin forever.
 *
 * IDEMPOTENCY: the outbox is at-least-once, so this handler CAN see the same event twice
 * (poller published, then died before stamping processedAt). The message id carries the outbox
 * row id, which is the natural dedup key if duplicate emails become a real problem.
 */
@Component
@RabbitListener(queues = RabbitConfig.EMAIL_QUEUE)
public class EmailNotificationConsumer {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationConsumer.class);
    private final JavaMailSender mailSender;
    private final LoanRepository loanRepository;
    // Sender identity, from readshelf.mail.from. Externalized because it's environment-specific:
    // dev points at MailHog and nobody checks the domain, prod must use a domain you control.
    private final String fromAddress;

    public EmailNotificationConsumer(JavaMailSender mailSender,
                                     LoanRepository loanRepository,
                                     @Value("${readshelf.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.loanRepository = loanRepository;
        this.fromAddress = fromAddress;
    }

    /**
     * @RabbitListener now sits on the CLASS and each @RabbitHandler claims one payload type.
     * Dispatch is by the deserialized type, which the converter resolves from the __TypeId__
     * header rather than from this method's signature — see RabbitConfig.EVENT_TYPE_MAPPING.
     *
     * This queue is bound to "loan.#", so it receives every loan event, not just this one.
     * Before, a single method typed to LoanRequestedEvent would have had loan.overdue coerced
     * into that shape with no error at all.
     */
    @RabbitHandler
    public void onLoanRequested(LoanRequestedEvent event) {
        // A loan that no longer exists is PERMANENT: no amount of retrying brings it back, so
        // reject outright instead of burning four attempts on it. Every other failure below is
        // left to throw naturally, which is what routes it into the backoff-and-retry path.
        // A projection rather than the entity — one query, and nothing lazy to blow up after
        // the session closes, since this listener runs outside any transaction.
        var view = loanRepository.findNotificationView(event.loanId())
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException(
                        "No loan found with id " + event.loanId()));
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(fromAddress);
        message.setTo(view.lenderEmail());
        message.setSubject("Loan Requested");
        message.setText(view.borrowerUsername() + " requested a loan");

        mailSender.send(message);
        log.debug("Sent loan requested notification to {}", view.lenderEmail());
    }

    /**
     * Mirror image of the one above: the lender acted, so the BORROWER is the one who needs to
     * hear about it. Same lookup, opposite pair of fields off the projection.
     */
    @RabbitHandler
    public void onLoanApproved(LoanApprovedEvent event) {
        var view = loanRepository.findNotificationView(event.loanId())
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException(
                        "No loan found with id " + event.loanId()));
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(fromAddress);
        message.setTo(view.borrowerEmail());
        message.setSubject("Loan Approved");
        message.setText(view.lenderUsername() + " approved your loan request");

        mailSender.send(message);
        log.debug("Sent loan approved notification to {}", view.borrowerEmail());
    }

    @RabbitHandler
    public void onLoanOverdue(LoanOverdueEvent event) {
        var view = loanRepository.findNotificationView(event.loanId())
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException(
                        "No loan found with id " + event.loanId()));
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(fromAddress);
        message.setTo(view.borrowerEmail());
        message.setSubject("Loan Overdue");
        // dueDate comes from the PAYLOAD, not from a re-read, and that's deliberate — it is the
        // one field here that isn't "current state". It's the fact this event asserts: the sweep
        // found this loan past due AS OF that date. If the loan were renewed between the sweep
        // and this send (retries, a replayed DLQ message), a re-read would put a FUTURE date in
        // an email telling someone they're late. Recipient details still come from the
        // projection, because those are current state — a changed email address should be used.
        message.setText("Your loan from " + view.lenderUsername()
                + " was due on " + event.dueDate());

        mailSender.send(message);
        log.debug("Sent loan overdue notification to {}", view.borrowerEmail());
    }

    /**
     * Catches loan events this consumer has no opinion about. Without it, an unclaimed type is
     * an error and the message dead-letters — but "the email consumer doesn't care about this
     * event" is normal operation on a queue bound to "loan.#", not a failure. Acking and
     * moving on is the correct behaviour; the DLQ is for things that went WRONG.
     */
    @RabbitHandler(isDefault = true)
    public void onUnhandledEvent(Object payload) {
        log.debug("Ignoring event this consumer doesn't handle: {}", payload.getClass().getSimpleName());
    }
}
