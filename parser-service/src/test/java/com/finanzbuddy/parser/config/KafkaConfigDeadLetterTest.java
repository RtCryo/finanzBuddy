package com.finanzbuddy.parser.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finanzbuddy.parser.client.GatewayStatusClient;
import com.finanzbuddy.parser.document.DocumentStatus;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.service.PersistenceOutcome;
import com.finanzbuddy.parser.service.ProcessedStatusReporter;
import com.finanzbuddy.parser.service.TransactionPersistenceService;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

class KafkaConfigDeadLetterTest {

    private final GatewayStatusClient gatewayStatusClient = mock(GatewayStatusClient.class);
    private final TransactionPersistenceService persistence =
            mock(TransactionPersistenceService.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);

    private DeadLetterPublishingRecoverer recoverer() {
        when(template.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        return new KafkaConfig()
                .deadLetterPublishingRecoverer(
                        template,
                        gatewayStatusClient,
                        new ProcessedStatusReporter(persistence, gatewayStatusClient),
                        persistence);
    }

    private static ConsumerRecord<String, Object> record() {
        return new ConsumerRecord<>(
                "documents.csv", 0, 0L, "1", new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1"));
    }

    @Test
    void storedDocumentIsReportedProcessedWithItsCounts() {
        when(persistence.isProcessed(1L)).thenReturn(true);
        when(persistence.findOutcome(1L)).thenReturn(Optional.of(new PersistenceOutcome(3, 4)));

        recoverer().accept(record(), new RuntimeException("status report failed"));

        verify(gatewayStatusClient).reportProcessed(1L, 3, 4);
        verify(persistence).markStatusConfirmed(1L);
        verify(gatewayStatusClient, never()).reportStatus(any(), any(), any());
    }

    @Test
    void unstoredDocumentIsReportedFailed() {
        when(persistence.isProcessed(1L)).thenReturn(false);

        recoverer().accept(record(), new IllegalArgumentException("bad file"));

        verify(gatewayStatusClient)
                .reportStatus(eq(1L), eq(DocumentStatus.FAILED), eq("IllegalArgumentException: bad file"));
        verify(gatewayStatusClient, never()).reportProcessed(any(), any(), any());
    }
}
