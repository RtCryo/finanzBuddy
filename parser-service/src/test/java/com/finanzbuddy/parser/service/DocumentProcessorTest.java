package com.finanzbuddy.parser.service;

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
import com.finanzbuddy.parser.repository.TransactionRepository;
import com.finanzbuddy.parser.storage.MinioStorageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class DocumentProcessorTest {

    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final MinioStorageService minioStorageService = mock(MinioStorageService.class);
    private final DocumentParserRouter parserRouter = mock(DocumentParserRouter.class);
    private final DocumentParser parser = mock(DocumentParser.class);
    private final GatewayStatusClient gatewayStatusClient = mock(GatewayStatusClient.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final DocumentProcessor processor =
            new DocumentProcessor(
                    transactionRepository,
                    minioStorageService,
                    parserRouter,
                    gatewayStatusClient,
                    Locale.GERMANY,
                    meterRegistry);

    @Test
    void ignoresDuplicateEvent() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        when(transactionRepository.existsByDocumentId(1L)).thenReturn(true);

        processor.process(event);

        verify(minioStorageService, never()).download(any());
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void reportsFailureWhenProcessingThrows() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        when(transactionRepository.existsByDocumentId(1L)).thenReturn(false);
        when(minioStorageService.download("csv/1"))
                .thenReturn(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));
        when(parserRouter.route("CSV")).thenReturn(parser);
        when(parser.parse(any(InputStream.class), any(), any()))
                .thenThrow(new IllegalArgumentException("parse error"));

        processor.process(event);

        verify(gatewayStatusClient).reportStatus(1L, DocumentStatus.PROCESSING, null);
        verify(gatewayStatusClient)
                .reportStatus(1L, DocumentStatus.FAILED, "IllegalArgumentException: parse error");
    }

    @Test
    void reportsFailureWhenParsingReturnsEmptyList() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        when(transactionRepository.existsByDocumentId(1L)).thenReturn(false);
        when(minioStorageService.download("csv/1"))
                .thenReturn(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));
        when(parserRouter.route("CSV")).thenReturn(parser);
        when(parser.parse(any(InputStream.class), any(), any())).thenReturn(List.of());

        processor.process(event);

        verify(gatewayStatusClient).reportStatus(1L, DocumentStatus.PROCESSING, null);
        verify(gatewayStatusClient)
                .reportStatus(
                        1L,
                        DocumentStatus.FAILED,
                        "IllegalArgumentException: No transactions found in document 1");
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void continuesWhenStatusReportingFails() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        when(transactionRepository.existsByDocumentId(1L)).thenReturn(false);
        doThrow(new RuntimeException("gateway down"))
                .when(gatewayStatusClient)
                .reportStatus(1L, DocumentStatus.PROCESSING, null);
        when(minioStorageService.download("csv/1"))
                .thenReturn(new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));
        when(parserRouter.route("CSV")).thenReturn(parser);
        Transaction transaction = new Transaction();
        when(parser.parse(any(InputStream.class), any(), any())).thenReturn(List.of(transaction));

        processor.process(event);

        verify(transactionRepository).saveAll(List.of(transaction));
        verify(gatewayStatusClient).reportStatus(1L, DocumentStatus.PROCESSED, null);
    }
}
