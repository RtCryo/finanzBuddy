package com.finanzbuddy.parser.processing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finanzbuddy.parser.document.DocumentStatus;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.parsing.domain.model.Transaction;
import com.finanzbuddy.parser.processing.domain.model.ProcessingOutcome;
import com.finanzbuddy.parser.processing.domain.model.WriteResult;
import com.finanzbuddy.parser.processing.port.out.FileFetchPort;
import com.finanzbuddy.parser.processing.port.out.ProcessingMetricsPort;
import com.finanzbuddy.parser.processing.port.out.StatusReporterPort;
import com.finanzbuddy.parser.processing.port.out.TransactionParserPort;
import com.finanzbuddy.parser.processing.port.out.TransactionWriterPort;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DocumentProcessorTest {

    private final FileFetchPort fileFetch = mock(FileFetchPort.class);
    private final TransactionParserPort transactionParser = mock(TransactionParserPort.class);
    private final TransactionWriterPort transactionWriter = mock(TransactionWriterPort.class);
    private final StatusReporterPort statusReporter = mock(StatusReporterPort.class);
    private final RecordingMetrics metrics = new RecordingMetrics();

    private final DocumentProcessor processor =
            new DocumentProcessor(
                    fileFetch,
                    transactionParser,
                    transactionWriter,
                    statusReporter,
                    metrics,
                    Locale.GERMANY);

    private static final Transaction SAMPLE =
            new Transaction(
                    1L, 2L, null, null, new BigDecimal("1.00"), "EUR", null, null, null, null);

    @Test
    void successfulProcessingWritesAndReportsProcessed() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        when(fileFetch.fetch("csv/1")).thenReturn(stream());
        when(transactionParser.parse(any(InputStream.class), eq(event), eq(Locale.GERMANY)))
                .thenReturn(List.of(SAMPLE));
        when(transactionWriter.writeIfNotProcessed(eq(1L), any())).thenReturn(WriteResult.WRITTEN);

        ProcessingOutcome outcome = processor.process(event);

        assertThat(outcome).isEqualTo(ProcessingOutcome.PROCESSED);
        verify(statusReporter).report(1L, DocumentStatus.PROCESSING, null);
        verify(statusReporter).report(1L, DocumentStatus.PROCESSED, null);
        assertThat(metrics.processed).isEqualTo(1);
    }

    @Test
    void ignoresDuplicateEventFromPreCheck() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        when(transactionWriter.isAlreadyProcessed(1L)).thenReturn(true);

        ProcessingOutcome outcome = processor.process(event);

        assertThat(outcome).isEqualTo(ProcessingOutcome.DUPLICATE);
        verify(statusReporter).report(1L, DocumentStatus.PROCESSED, null);
        verify(fileFetch, never()).fetch(any());
        verify(transactionWriter, never()).writeIfNotProcessed(any(), any());
        assertThat(metrics.duplicate).isEqualTo(1);
    }

    @Test
    void treatsConcurrentWriteAsDuplicate() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        when(fileFetch.fetch("csv/1")).thenReturn(stream());
        when(transactionParser.parse(any(InputStream.class), eq(event), eq(Locale.GERMANY)))
                .thenReturn(List.of(SAMPLE));
        when(transactionWriter.writeIfNotProcessed(eq(1L), any()))
                .thenReturn(WriteResult.DUPLICATE);

        ProcessingOutcome outcome = processor.process(event);

        assertThat(outcome).isEqualTo(ProcessingOutcome.DUPLICATE);
        verify(statusReporter).report(1L, DocumentStatus.PROCESSED, null);
        assertThat(metrics.duplicate).isEqualTo(1);
        assertThat(metrics.processed).isZero();
    }

    @Test
    void recordsFailureWhenParsingThrows() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        when(fileFetch.fetch("csv/1")).thenReturn(stream());
        when(transactionParser.parse(any(InputStream.class), eq(event), eq(Locale.GERMANY)))
                .thenThrow(new IllegalArgumentException("parse error"));

        assertThatThrownBy(() -> processor.process(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parse error");

        verify(statusReporter).report(1L, DocumentStatus.PROCESSING, null);
        verify(statusReporter, never()).report(eq(1L), eq(DocumentStatus.FAILED), any());
        assertThat(metrics.failed).isEqualTo(1);
    }

    @Test
    void recordsFailureWhenParsingReturnsEmptyList() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        when(fileFetch.fetch("csv/1")).thenReturn(stream());
        when(transactionParser.parse(any(InputStream.class), eq(event), eq(Locale.GERMANY)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> processor.process(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No transactions found in document 1");

        verify(statusReporter).report(1L, DocumentStatus.PROCESSING, null);
        verify(transactionWriter, never()).writeIfNotProcessed(any(), any());
        assertThat(metrics.failed).isEqualTo(1);
    }

    @Test
    void wrapsProcessingStatusReportingFailure() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        doThrow(new RuntimeException("gateway down"))
                .when(statusReporter)
                .report(1L, DocumentStatus.PROCESSING, null);

        assertThatThrownBy(() -> processor.process(event))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(RuntimeException.class);

        verify(fileFetch, never()).fetch(any());
        verify(transactionWriter, never()).writeIfNotProcessed(any(), any());
        assertThat(metrics.failed).isEqualTo(1);
    }

    @Test
    void rejectsInvalidEventsBeforeCallingDependencies() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(null, 2L, "CSV", "csv/1");

        assertThatThrownBy(() -> processor.process(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive documentId");

        verify(fileFetch, never()).fetch(any());
        assertThat(metrics.failed).isZero();
    }

    private static InputStream stream() {
        return new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8));
    }

    private static final class RecordingMetrics implements ProcessingMetricsPort {
        private int processed;
        private int failed;
        private int duplicate;

        @Override
        public void recordProcessed() {
            processed++;
        }

        @Override
        public void recordFailed() {
            failed++;
        }

        @Override
        public void recordDuplicate() {
            duplicate++;
        }

        @Override
        public <T> T timeProcessing(Supplier<T> processing) {
            return processing.get();
        }
    }
}
