package com.finanzbuddy.api.document.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finanzbuddy.api.document.adapter.out.persistence.OutboxEvent;
import com.finanzbuddy.api.document.adapter.out.persistence.OutboxEventRepository;
import com.finanzbuddy.api.document.domain.model.Document;
import com.finanzbuddy.api.document.port.out.DocumentEventPort;
import com.finanzbuddy.api.event.DocumentUploadedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventAdapter implements DocumentEventPort {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final String topicPrefix;

    public OutboxEventAdapter(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            @Value("${app.kafka.document-topic-prefix}") String topicPrefix) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.topicPrefix = topicPrefix;
    }

    @Override
    public void enqueueUploadedEvent(Document document) {
        String topic = topicPrefix + "." + document.type().toLowerCase();
        DocumentUploadedEvent event =
                new DocumentUploadedEvent(
                        document.id(), document.ownerId(), document.type(), document.storagePath());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize DocumentUploadedEvent for document " + document.id(), e);
        }
        outboxEventRepository.save(new OutboxEvent(document.id(), topic, payload));
    }
}
