package com.readshelf.outbox;

import com.readshelf.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * The consumer half of the transactional outbox. On a timer it drains unpublished events
 * from the outbox table, publishes each to the topic exchange, and stamps processedAt so
 * the next tick skips it.
 *
 * Why a separate poller instead of publishing inline in create(): the write side only has
 * to guarantee the event is DURABLY STORED with the loan (one atomic tx). Actually shipping
 * it to a broker — which can be slow or down — is decoupled to here, where a failure just
 * means the row stays unprocessed and gets retried next tick.
 *
 * DELIVERY SEMANTICS: this is AT-LEAST-ONCE, and that is not a bug to fix here. If the
 * broker accepts a message but the app dies before processedAt commits, the next tick
 * republishes it. Exactly-once across two systems isn't achievable; the standard answer is
 * to make CONSUMERS IDEMPOTENT (handling the same event twice is a no-op).
 */
@Component
public class OutboxPoller {
    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPoller(OutboxRepository outboxRepository, RabbitTemplate rabbitTemplate) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    // fixedDelay = wait 5s AFTER the previous run finishes before starting the next
    // (vs fixedRate, which fires every 5s regardless and can overlap). fixedDelay keeps
    // ticks from stacking up if one run runs long.
    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        var pendingEvents = outboxRepository.findByProcessedAtIsNullOrderByCreatedAtAsc();
        for (var event : pendingEvents) {
            // The routing key was decided by the producer and stored on the row (V15), so the
            // poller stays dumb: it ships what it's told, and has no opinion about topology.
            String routingKey = event.getRoutingKey();

            // Send the stored payload as raw bytes rather than via a message converter: the
            // outbox payload is ALREADY a JSON string, so converting it would produce a JSON
            // string CONTAINING JSON (double-encoded). The explicit content type is what tells
            // the consumer side's JacksonJsonMessageConverter that this body is JSON.
            Message message = MessageBuilder
                    .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding(StandardCharsets.UTF_8.name())
                    // PERSISTENT = the broker writes the message to disk, so it survives a
                    // broker restart. A durable queue alone does not save the messages in it.
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    // Carries the outbox row id to the consumer, which is the natural
                    // dedup key for the idempotency the at-least-once note above demands.
                    .setMessageId(event.getId().toString())
                    // Names the event type on the wire so the converter knows WHICH record to
                    // deserialize into. A queue bound to "loan.#" carries several event types,
                    // and without this the converter would just trust the handler's parameter
                    // type and quietly coerce an overdue event into a requested one.
                    // RabbitConfig.EVENT_TYPE_MAPPING is the other half of this handshake.
                    .setHeader(DefaultJacksonJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME,
                            event.getEventType())
                    .build();

            rabbitTemplate.send(RabbitConfig.EXCHANGE, routingKey, message);
            log.info("Published outbox event {} to {} with routing key {}",
                    event.getId(), RabbitConfig.EXCHANGE, routingKey);

            event.setProcessedAt(Instant.now());
            outboxRepository.save(event);
        }
    }

}
