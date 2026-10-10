package com.finanzbuddy.parser.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finanzbuddy.parser.client.GatewayStatusClient;
import com.finanzbuddy.parser.document.DocumentStatus;
import com.finanzbuddy.parser.entity.Transaction;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.parser.DocumentParser;
import com.finanzbuddy.parser.parser.DocumentParserRouter;
import com.finanzbuddy.parser.storage.MinioStorageService;
import com.finanzbuddy.parser.storage.StorageException;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class DocumentProcessorTest {

    private final TransactionPersistenceService transactionPersistenceService =
            mock(TransactionPersistenceService.class);
    private final MinioStorageService minioStorageService = mock(MinioStorageService.class);
    private final DocumentParserRouter parserRouter = mock(DocumentParserRouter.class);
    private final DocumentParser parser = mock(DocumentParser.class);
    private final GatewayStatusClient gatewayStatusClient = mock(GatewayStatusClient.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final DocumentProcessor processor =
            new DocumentProcessor(
                    transactionPersistenceService,
                    minioStorageService,
                    parserRouter,
                    gatewayStatusClient,
                    Locale.GERMANY,
                    meterRegistry);

    @Test
    void ignoresDuplicateEvent() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        when(transactionPersistenceService.isProcessed(1L)).thenReturn(true);

        processor.process(event);

        verify(minioStorageService, never()).download(any());
        verify(transactionPersistenceService, never()).saveIfNotProcessed(any(), any());
    }

    @Test
    void countsConcurrentDuplicateAsDuplicateForItsFileType() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        when(minioStorageService.download("csv/1"))
                .thenReturn(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));
        when(parserRouter.route("CSV")).thenReturn(parser);
        when(parser.parse(any(InputStream.class), any(), any()))
                .thenReturn(List.of(new Transaction()));
        when(transactionPersistenceService.saveIfNotProcessed(any(), any()))
                .thenThrow(new DuplicateDocumentException(1L));

        processor.process(event);

        org.assertj.core.api.Assertions.assertThat(
                        meterRegistry
                                .get("parser.documents.duplicate")
                                .tag("type", "CSV")
                                .counter()
                                .count())
                .isEqualTo(1.0);
        verify(gatewayStatusClient).reportStatus(1L, DocumentStatus.PROCESSED, null);
    }

    @Test
    void countsTransactionsFromSuccessfullyProcessedDocuments() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        when(minioStorageService.download("csv/1"))
                .thenReturn(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));
        when(parserRouter.route("CSV")).thenReturn(parser);
        when(parser.parse(any(InputStream.class), any(), any()))
                .thenReturn(List.of(new Transaction(), new Transaction()));
        when(transactionPersistenceService.saveIfNotProcessed(any(), any())).thenReturn(true);

        processor.process(event);

        org.assertj.core.api.Assertions.assertThat(
                        meterRegistry
                                .get("parser.transactions.parsed")
                                .tag("type", "CSV")
                                .counter()
                                .count())
                .isEqualTo(2.0);
        org.assertj.core.api.Assertions.assertThat(
                        meterRegistry
                                .get("parser.transactions.parsed")
                                .tag("type", "CSV")
                                .counter()
                                .getId()
                                .getTags())
                .containsExactly(Tag.of("type", "CSV"));
        org.assertj.core.api.Assertions.assertThat(
                        meterRegistry
                                .get("parser.documents.processed")
                                .tag("type", "CSV")
                                .counter()
                                .count())
                .isEqualTo(1.0);
        org.assertj.core.api.Assertions.assertThat(
                        meterRegistry
                                .get("parser.documents.processing.duration")
                                .tag("type", "CSV")
                                .timer()
                                .count())
                .isEqualTo(1L);
    }

    @Test
    void reportsFailureWhenProcessingThrows() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        when(minioStorageService.download("csv/1"))
                .thenReturn(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));
        when(parserRouter.route("CSV")).thenReturn(parser);
        when(parser.parse(any(InputStream.class), any(), any()))
                .thenThrow(new IllegalArgumentException("parse error"));

        assertThatThrownBy(() -> processor.process(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parse error");

        verify(gatewayStatusClient).reportStatus(1L, DocumentStatus.PROCESSING, null);
        verify(gatewayStatusClient, never())
                .reportStatus(1L, DocumentStatus.FAILED, "IllegalArgumentException: parse error");
    }

    @Test
    void recordsStorageFailureForFileType() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        when(minioStorageService.download("csv/1"))
                .thenThrow(new StorageException("storage unavailable", new IOException("down")));

        assertThatThrownBy(() -> processor.process(event))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("storage unavailable");

        org.assertj.core.api.Assertions.assertThat(
                        meterRegistry
                                .get("parser.documents.failed")
                                .tag("type", "CSV")
                                .counter()
                                .count())
                .isEqualTo(1.0);
    }

    @Test
    void recordsDatabaseFailureForFileType() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        when(minioStorageService.download("csv/1"))
                .thenReturn(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));
        when(parserRouter.route("CSV")).thenReturn(parser);
        when(parser.parse(any(InputStream.class), any(), any()))
                .thenReturn(List.of(new Transaction()));
        when(transactionPersistenceService.saveIfNotProcessed(any(), any()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(() -> processor.process(event))
                .isInstanceOf(DataAccessResourceFailureException.class)
                .hasMessageContaining("database unavailable");

        org.assertj.core.api.Assertions.assertThat(
                        meterRegistry
                                .get("parser.documents.failed")
                                .tag("type", "CSV")
                                .counter()
                                .count())
                .isEqualTo(1.0);
    }

    @Test
    void reportsFailureWhenParsingReturnsEmptyList() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        when(minioStorageService.download("csv/1"))
                .thenReturn(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));
        when(parserRouter.route("CSV")).thenReturn(parser);
        when(parser.parse(any(InputStream.class), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> processor.process(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No transactions found in document 1");

        verify(gatewayStatusClient).reportStatus(1L, DocumentStatus.PROCESSING, null);
        verify(gatewayStatusClient, never()).reportStatus(1L, DocumentStatus.FAILED, null);
        verify(transactionPersistenceService, never()).saveIfNotProcessed(any(), any());
        org.assertj.core.api.Assertions.assertThat(
                        meterRegistry
                                .get("parser.documents.failed")
                                .tag("type", "CSV")
                                .counter()
                                .count())
                .isEqualTo(1.0);
    }

    @Test
    void mapsUnsupportedDocumentTypesToOtherMetricLabel() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "TXT", "text/1");
        when(minioStorageService.download("text/1"))
                .thenReturn(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));
        when(parserRouter.route("TXT"))
                .thenThrow(new IllegalArgumentException("unsupported document type"));

        assertThatThrownBy(() -> processor.process(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported document type");

        org.assertj.core.api.Assertions.assertThat(
                        meterRegistry
                                .get("parser.documents.failed")
                                .tag("type", "OTHER")
                                .counter()
                                .getId()
                                .getTags())
                .containsExactly(Tag.of("type", "OTHER"));
    }

    @Test
    void recordsPdfAndXmlAttemptsUnderTheirOwnTypeLabels() {
        for (String type : List.of("PDF", "XML")) {
            String storagePath = type.toLowerCase(Locale.ROOT) + "/1";
            DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, type, storagePath);
            when(minioStorageService.download(storagePath))
                    .thenReturn(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));
            when(parserRouter.route(type))
                    .thenThrow(new UnsupportedOperationException("format is disabled"));

            assertThatThrownBy(() -> processor.process(event))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("format is disabled");

            org.assertj.core.api.Assertions.assertThat(
                            meterRegistry
                                    .get("parser.documents.failed")
                                    .tag("type", type)
                                    .counter()
                                    .count())
                    .isEqualTo(1.0);
        }
    }

    @Test
    void retriesBeforeProcessingWhenProcessingStatusReportingFails() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        doThrow(new RuntimeException("gateway down"))
                .when(gatewayStatusClient)
                .reportStatus(1L, DocumentStatus.PROCESSING, null);
        assertThatThrownBy(() -> processor.process(event))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(RuntimeException.class);

        verify(minioStorageService, never()).download(any());
        verify(transactionPersistenceService, never()).saveIfNotProcessed(any(), any());
    }

    @Test
    void retriesProcessedStatusForAlreadyPersistedDocument() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        when(transactionPersistenceService.isProcessed(1L)).thenReturn(true);

        processor.process(event);

        verify(gatewayStatusClient).reportStatus(1L, DocumentStatus.PROCESSED, null);
        verify(minioStorageService, never()).download(any());
    }

    @Test
    void rejectsInvalidEventsBeforeCallingDependencies() {
        assertThatThrownBy(() -> processor.process(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event must not be null");
        assertThatThrownBy(
                        () ->
                                processor.process(
                                        new DocumentUploadedEvent(null, 2L, "CSV", "csv/1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive documentId");
        assertThatThrownBy(
                        () -> processor.process(new DocumentUploadedEvent(0L, 2L, "CSV", "csv/1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive documentId");
        assertThatThrownBy(
                        () ->
                                processor.process(
                                        new DocumentUploadedEvent(1L, null, "CSV", "csv/1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive userId");
        assertThatThrownBy(
                        () -> processor.process(new DocumentUploadedEvent(1L, 0L, "CSV", "csv/1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive userId");
        assertThatThrownBy(
                        () -> processor.process(new DocumentUploadedEvent(1L, 2L, null, "csv/1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("document type");
        assertThatThrownBy(() -> processor.process(new DocumentUploadedEvent(1L, 2L, " ", "csv/1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("document type");
        assertThatThrownBy(() -> processor.process(new DocumentUploadedEvent(1L, 2L, "CSV", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storage path");
        assertThatThrownBy(() -> processor.process(new DocumentUploadedEvent(1L, 2L, "CSV", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storage path");

        verify(minioStorageService, never()).download(any());
        verify(transactionPersistenceService, never()).isProcessed(any());
    }
}
