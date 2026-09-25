package com.finanzbuddy.api.document.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finanzbuddy.api.entity.OutboxEvent;
import com.finanzbuddy.api.event.DocumentUploadedEvent;
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
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int batchSize;
    private final long sendTimeoutMs;

    public OutboxRelay(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<Object, Object> kafkaTemplate,
            @Value("${app.outbox.batch-size:100}") int batchSize,
            @Value("${app.outbox.send-timeout-ms:10000}") long sendTimeoutMs) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending =
                outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(
                        PageRequest.of(0, batchSize));
        for (OutboxEvent event : pending) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEvent event) {
        try {
            DocumentUploadedEvent payload =
                    objectMapper.readValue(event.getPayload(), DocumentUploadedEvent.class);
            kafkaTemplate
                    .send(event.getTopic(), payload)
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            event.markPublished();
            log.info(
                    "Published outbox event {} to topic {} for document {}",
                    event.getId(),
                    event.getTopic(),
                    event.getAggregateId());
        } catch (Exception e) {
            event.recordFailedAttempt();
            log.error(
                    "Failed to publish outbox event {} to topic {} for document {} (attempt {})",
                    event.getId(),
                    event.getTopic(),
                    event.getAggregateId(),
                    event.getAttempts(),
                    e);
        }
    }
}
