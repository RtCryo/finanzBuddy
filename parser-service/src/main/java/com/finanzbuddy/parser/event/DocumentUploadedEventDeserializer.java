package com.finanzbuddy.parser.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentUploadedEventDeserializer implements Deserializer<Object> {

    private static final Logger logger =
            LoggerFactory.getLogger(DocumentUploadedEventDeserializer.class);

    private final ObjectMapper objectMapper;

    public DocumentUploadedEventDeserializer() {
        this(new ObjectMapper());
    }

    public DocumentUploadedEventDeserializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public DocumentUploadedEvent deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.readValue(
                    new String(data, StandardCharsets.UTF_8), DocumentUploadedEvent.class);
        } catch (Exception e) {
            logger.error("Failed to deserialize DocumentUploadedEvent from topic {}", topic, e);
            throw new IllegalArgumentException("Invalid DocumentUploadedEvent payload", e);
        }
    }
}
