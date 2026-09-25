package com.finanzbuddy.api.document.outbox;

import com.finanzbuddy.api.entity.OutboxEvent;
import com.finanzbuddy.api.repository.OutboxEventRepository;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final TransactionTemplate txTemplate;
    private final OutboxMetrics outboxMetrics;
    private final int batchSize;
    private final long sendTimeoutMs;
    private final int maxAttempts;

    public OutboxRelay(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<Object, Object> kafkaTemplate,
            PlatformTransactionManager txManager,
            OutboxMetrics outboxMetrics,
            @Value("${app.outbox.batch-size:100}") int batchSize,
            @Value("${app.outbox.send-timeout-ms:3000}") long sendTimeoutMs,
            @Value("${app.outbox.max-attempts:10}") int maxAttempts) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.txTemplate = new TransactionTemplate(txManager);
        this.outboxMetrics = outboxMetrics;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    public void publishPending() {
        List<OutboxEvent> pending =
                txTemplate.execute(
                        status ->
                                outboxEventRepository
                                        .findByPublishedAtIsNullAndDeadLetteredAtIsNullOrderByCreatedAtAsc(
                                                PageRequest.of(0, batchSize)));
        if (pending == null) {
            return;
        }
        // The blocking broker round-trip happens outside any transaction so a slow or unreachable
        // broker never pins a DB connection while events are being sent. Every event in the batch
        // is attempted (a failing event at the head must not block newer, deliverable ones); the
        // batch size and per-send timeout bound the work when the broker is unhealthy.
        for (OutboxEvent event : pending) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEvent event) {
        // Send the durably stored JSON payload verbatim (StringSerializer) so the bytes on the wire
        // are exactly the bytes committed to the outbox — no re-serialization and no Java-class type
        // headers that would couple consumers to this module's package.
        try {
            kafkaTemplate
                    .send(event.getTopic(), event.getPayload())
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            recordFailure(event, e);
            return;
        }

        markPublished(event.getId());
        log.info(
                "Published outbox event {} to topic {} for document {}",
                event.getId(),
                event.getTopic(),
                event.getAggregateId());
    }

    private void recordFailure(OutboxEvent event, Exception cause) {
        int attempts =
                txTemplate.execute(
                        status -> {
                            OutboxEvent managed =
                                    outboxEventRepository.findById(event.getId()).orElse(null);
                            if (managed == null) {
                                return 0;
                            }
                            managed.recordFailedAttempt();
                            if (managed.getAttempts() >= maxAttempts) {
                                managed.deadLetter();
                            }
                            return managed.getAttempts();
                        });
        if (attempts >= maxAttempts) {
            outboxMetrics.recordDeadLettered();
            log.error(
                    "Dead-lettering outbox event {} to topic {} for document {} after {} failed"
                            + " attempts",
                    event.getId(),
                    event.getTopic(),
                    event.getAggregateId(),
                    attempts,
                    cause);
        } else {
            log.error(
                    "Failed to publish outbox event {} to topic {} for document {} (attempt {})",
                    event.getId(),
                    event.getTopic(),
                    event.getAggregateId(),
                    attempts,
                    cause);
        }
    }

    private void markPublished(Long id) {
        txTemplate.executeWithoutResult(
                status -> outboxEventRepository.findById(id).ifPresent(OutboxEvent::markPublished));
    }
}
