package com.readshelf.config;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import com.readshelf.event.BookCreatedEvent;
import com.readshelf.event.BookUpdatedEvent;
import com.readshelf.event.LoanApprovedEvent;
import com.readshelf.event.LoanOverdueEvent;
import com.readshelf.event.LoanRequestedEvent;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * AMQP topology: ONE topic exchange, one queue per logical consumer.
 *
 * Why queue-per-consumer and not one shared queue: RabbitMQ round-robins each message to
 * exactly ONE consumer on a queue (competing consumers). Two DIFFERENT consumers sharing a
 * queue would therefore split the stream — roughly half the books indexed, half emailed.
 * A queue is a worklist owned by one KIND of worker; scaling that worker to N instances is
 * when competing consumers becomes the feature you want, and those N do share the queue.
 *
 * Why one topic exchange and not a fanout alongside it: the producer must publish once and
 * stay ignorant of who listens. A second exchange would force the poller to publish each
 * message twice and to know the consumer topology — exactly the coupling an exchange exists
 * to remove. "Everything" is expressible as the topic pattern "#", so fanout buys nothing.
 *
 * Routing keys are dotted words (loan.requested, book.created); binding patterns match them
 * word-by-word, where * = exactly one word and # = zero or more words.
 *
 *   poller -> [readshelf.events (topic)] --"loan.#"--> readshelf.email.queue
 *
 * DEFERRED QUEUES: readshelf.search.queue ("book.#") belongs to Phase 12, which owns the
 * Elasticsearch index mapping and query side; readshelf.webhook.queue ("#") belongs to Phase 16,
 * which owns webhook_subscriptions, HMAC signing, and delivery records. Neither queue is declared
 * here, because a queue with no consumer is just a growing pile — buffering events for a consumer
 * two phases away is storing garbage, not planning ahead. Each phase declares its own queue and
 * binding when it declares its consumer, and NO producer changes when it does. That is the whole
 * point of publishing to an exchange instead of to a queue.
 *
 * Consequence to be aware of: until those queues exist, "book.created" matches no binding and
 * RabbitMQ DISCARDS it silently — no error, no dead letter. Fine while nothing needs those events;
 * see the V15 migration comment for why unroutable-means-gone is worth respecting.
 *
 * FAILURE PATH: queues dead-letter to a SINGLE shared DLQ. The queue-per-consumer
 * argument above doesn't carry over, because it rests on competing consumers — and nothing
 * consumes a DLQ. It's an inspection bucket a human reads, and one bucket is easier to watch
 * than three. Provenance isn't lost: RabbitMQ stamps an `x-death` header recording the
 * original queue, and (since we don't override the routing key) the dead-lettered message
 * keeps the key it was published with.
 *
 *   any queue --(retries exhausted)--> [readshelf.events.dlx] --"#"--> readshelf.dlq
 *
 * Everything here is declared idempotently: Spring's RabbitAdmin (auto-configured) replays
 * these declarations on every connection, so a fresh broker self-provisions and an existing
 * one is left alone. Declarations must MATCH what's on the broker, though — changing a
 * queue's durability/arguments later requires deleting the queue first, or the declaration
 * fails with a PRECONDITION_FAILED channel error.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "readshelf.events";

    public static final String EMAIL_QUEUE = "readshelf.email.queue";

    // Where a message goes once the listener has given up on it.
    public static final String DLX = "readshelf.events.dlx";
    public static final String DLQ = "readshelf.dlq";

    // Binding patterns — the "what do I care about?" declaration for each consumer.
    private static final String LOAN_EVENTS = "loan.#";
    private static final String ALL_EVENTS = "#";

    @Bean
    TopicExchange eventsExchange() {
        // durable = survives a broker restart; non-auto-delete = survives having zero queues
        // bound. Both are the defaults for `new TopicExchange(name)`, spelled out here because
        // a non-durable exchange silently vanishing on restart is a miserable thing to debug.
        return new TopicExchange(EXCHANGE, true, false);
    }

    // deadLetterExchange() sets the x-dead-letter-exchange argument on the queue. We do NOT
    // also set deadLetterRoutingKey: leaving it off makes RabbitMQ re-publish the message to
    // the DLX under its ORIGINAL routing key, so "loan.requested" is still visible in the DLQ.
    @Bean
    Queue emailQueue() {
        return QueueBuilder.durable(EMAIL_QUEUE).deadLetterExchange(DLX).build();
    }

    // A durable QUEUE means the queue definition survives a broker restart. That is separate
    // from whether the MESSAGES in it survive — that's the message's own persistent delivery
    // mode, which RabbitTemplate sets by default. You need both to actually keep messages.

    @Bean
    TopicExchange deadLetterExchange() {
        return new TopicExchange(DLX, true, false);
    }

    @Bean
    Queue deadLetterQueue() {
        // No dead-letter-exchange of its own — a DLQ that dead-letters is a loop.
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(ALL_EVENTS);
    }

    @Bean
    Binding emailBinding(Queue emailQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(emailQueue).to(eventsExchange).with(LOAN_EVENTS);
    }

    /**
     * Lets @RabbitListener methods declare a typed parameter (a record) and have the JSON body
     * deserialized into it, instead of hand-parsing a byte[].
     *
     * JacksonJsonMessageConverter is the Jackson 3 converter; Jackson2JsonMessageConverter is
     * the legacy Jackson 2 one (see the Jackson 3 / Spring Boot 4 note in STATUS.md).
     *
     * NOTE this converter is deliberately NOT used on the publish side. The outbox payload is
     * ALREADY a JSON string, so handing it to a converter would serialize a string containing
     * JSON — i.e. double-encoded, arriving as "{\"loanId\":...}". The poller therefore sends
     * the raw bytes itself with an explicit application/json content type, which is also what
     * tells this converter on the receiving end that the body is JSON.
     */
    /**
     * Maps the outbox's event_type onto the record it should deserialize into.
     *
     * Needed because a queue holds MIXED event types: the email queue is bound to "loan.#", so
     * loan.requested and loan.overdue both land there. Without this, the converter's default
     * behaviour (TypePrecedence.INFERRED) deserializes into whatever the handler method's
     * parameter type happens to be — which silently forces an overdue event into a
     * LoanRequestedEvent shape. TYPE_ID instead reads the __TypeId__ header the poller stamps
     * and picks the class from this map, which is what makes @RabbitHandler dispatch work.
     *
     * ADD AN ENTRY HERE for every new event type. An id with no mapping fails to deserialize
     * and ends up in the DLQ.
     */
    public static final Map<String, Class<?>> EVENT_TYPE_MAPPING = Map.of(
            "LOAN_REQUESTED", LoanRequestedEvent.class,
            "LOAN_APPROVED", LoanApprovedEvent.class,
            "LOAN_OVERDUE", LoanOverdueEvent.class,
            // Mapped even though no queue is bound to book.# until Phase 12 — the mapping is about
            // how to DESERIALIZE a type, not about who receives it. Having it ready means Phase 12
            // adds a queue and a handler, and touches nothing here.
            "BOOK_CREATED", BookCreatedEvent.class,
            "BOOK_UPDATED", BookUpdatedEvent.class
    );

    @Bean
    MessageConverter jsonMessageConverter() {
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.TYPE_ID);
        typeMapper.setIdClassMapping(EVENT_TYPE_MAPPING);
        // Deserializing a class named by an inbound header is a remote-code-execution shape if
        // the sender is untrusted. Restricting to our own event package keeps a malicious
        // __TypeId__ from instantiating something dangerous.
        typeMapper.setTrustedPackages("com.readshelf.event");

        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    /**
     * Makes AmqpRejectAndDontRequeueException actually skip the retries.
     *
     * Enabling listener retry wraps each listener in an interceptor whose policy retries on ANY
     * exception — it has never heard of AmqpRejectAndDontRequeueException. That exception only
     * means something to the error handler that runs AFTER retries are exhausted, where it
     * selects "reject to the DLX" over "requeue". So without this bean, a known-permanent
     * failure still burns the full backoff (2s + 4s + 8s) before dead-lettering.
     *
     * The predicate answers "should this be retried?", so returning false on a permanent
     * failure sends it straight to the DLQ on attempt 1.
     */
    @Bean
    RabbitListenerRetrySettingsCustomizer skipRetryOnPermanentFailures() {
        return settings -> settings.setExceptionPredicate(RabbitConfig::isRetryable);
    }

    // Walks the cause chain because the listener's exception reaches the interceptor wrapped in
    // a ListenerExecutionFailedException — checking only the top-level type would never match.
    private static boolean isRetryable(Throwable thrown) {
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            // Explicitly-signalled permanent failure (e.g. the referenced entity is gone).
            if (t instanceof AmqpRejectAndDontRequeueException) {
                return false;
            }
            // Conversion blew up BEFORE the listener ran: these same bytes will fail to
            // deserialize identically on every attempt, so retrying only delays the DLQ.
            // IllegalArgumentException is included because that's what the type mapper throws
            // for an unknown/untrusted __TypeId__ — an unmapped event type, which is a code
            // fix (add it to EVENT_TYPE_MAPPING), never something that resolves on retry.
            if (t instanceof MessageConversionException || t instanceof IllegalArgumentException) {
                return false;
            }
            if (t.getCause() == t) {
                break; // self-referential cause; stop rather than spin
            }
        }
        return true;
    }
}
