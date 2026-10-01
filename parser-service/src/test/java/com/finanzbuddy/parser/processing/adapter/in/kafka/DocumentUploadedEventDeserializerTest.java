package com.finanzbuddy.parser.processing.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DocumentUploadedEventDeserializerTest {

    private final DocumentUploadedEventDeserializer deserializer =
            new DocumentUploadedEventDeserializer();

    @Test
    void deserializesValidEvent() {
        String json = "{\"documentId\":1,\"userId\":2,\"type\":\"CSV\",\"storagePath\":\"csv/1\"}";

        DocumentUploadedEvent event =
                deserializer.deserialize("documents.csv", json.getBytes(StandardCharsets.UTF_8));

        assertThat(event.documentId()).isEqualTo(1L);
        assertThat(event.userId()).isEqualTo(2L);
        assertThat(event.type()).isEqualTo("CSV");
        assertThat(event.storagePath()).isEqualTo("csv/1");
    }

    @Test
    void returnsNullForNullData() {
        assertThat(deserializer.deserialize("documents.csv", null)).isNull();
    }

    @Test
    void throwsOnInvalidPayload() {
        byte[] invalid = "not-json".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> deserializer.deserialize("documents.csv", invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid DocumentUploadedEvent payload");
    }
}
