package com.finanzbuddy.parser.processing.domain;

import com.finanzbuddy.parser.document.DocumentStatus;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.parsing.domain.model.Transaction;
import com.finanzbuddy.parser.processing.domain.exception.FileFetchException;
import com.finanzbuddy.parser.processing.domain.exception.TransactionPersistenceException;
import com.finanzbuddy.parser.processing.domain.model.ProcessingOutcome;
import com.finanzbuddy.parser.processing.domain.model.WriteResult;
import com.finanzbuddy.parser.processing.port.in.ProcessDocumentUseCase;
import com.finanzbuddy.parser.processing.port.out.FileFetchPort;
import com.finanzbuddy.parser.processing.port.out.ProcessingMetricsPort;
import com.finanzbuddy.parser.processing.port.out.StatusReporterPort;
import com.finanzbuddy.parser.processing.port.out.TransactionParserPort;
import com.finanzbuddy.parser.processing.port.out.TransactionWriterPort;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Framework-free orchestration of the consume -> fetch -> parse -> persist -> report flow. */
public class DocumentProcessor implements ProcessDocumentUseCase {

    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessor.class);

    private final FileFetchPort fileFetch;
    private final TransactionParserPort transactionParser;
    private final TransactionWriterPort transactionWriter;
    private final StatusReporterPort statusReporter;
    private final ProcessingMetricsPort metrics;
    private final Locale defaultLocale;

    public DocumentProcessor(
            FileFetchPort fileFetch,
            TransactionParserPort transactionParser,
            TransactionWriterPort transactionWriter,
            StatusReporterPort statusReporter,
            ProcessingMetricsPort metrics,
            Locale defaultLocale) {
        this.fileFetch = fileFetch;
        this.transactionParser = transactionParser;
        this.transactionWriter = transactionWriter;
        this.statusReporter = statusReporter;
        this.metrics = metrics;
        this.defaultLocale = defaultLocale;
    }

    @Override
    public ProcessingOutcome process(DocumentUploadedEvent event) {
        validate(event);
        logger.info(
                "Processing document: documentId={}, type={}", event.documentId(), event.type());
        return metrics.timeProcessing(() -> doProcess(event));
    }

    private ProcessingOutcome doProcess(DocumentUploadedEvent event) {
        try {
            if (transactionWriter.isAlreadyProcessed(event.documentId())) {
                logger.info("Duplicate event ignored for documentId={}", event.documentId());
                statusReporter.report(event.documentId(), DocumentStatus.PROCESSED, null);
                metrics.recordDuplicate();
                return ProcessingOutcome.DUPLICATE;
            }

            statusReporter.report(event.documentId(), DocumentStatus.PROCESSING, null);
            try (InputStream content = fileFetch.fetch(event.storagePath())) {
                List<Transaction> parsed = transactionParser.parse(content, event, defaultLocale);

                if (parsed.isEmpty()) {
                    throw new IllegalArgumentException(
                            "No transactions found in document " + event.documentId());
                }

                WriteResult writeResult =
                        transactionWriter.writeIfNotProcessed(event.documentId(), parsed);
                statusReporter.report(event.documentId(), DocumentStatus.PROCESSED, null);
                if (writeResult == WriteResult.DUPLICATE) {
                    logger.info("Duplicate event ignored for documentId={}", event.documentId());
                    metrics.recordDuplicate();
                    return ProcessingOutcome.DUPLICATE;
                }
                metrics.recordProcessed();
                logger.info(
                        "Document processed successfully: documentId={}, transactions={}",
                        event.documentId(),
                        parsed.size());
                return ProcessingOutcome.PROCESSED;
            }
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            metrics.recordFailed();
            throw e;
        } catch (FileFetchException | TransactionPersistenceException e) {
            logger.error(
                    "Transient failure processing document: documentId={}", event.documentId(), e);
            metrics.recordFailed();
            throw e;
        } catch (Exception e) {
            logger.error(
                    "Unexpected failure processing document: documentId={}", event.documentId(), e);
            metrics.recordFailed();
            throw new IllegalStateException("Failed to process document " + event.documentId(), e);
        }
    }

    private void validate(DocumentUploadedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Document event must not be null");
        }
        if (event.documentId() == null || event.documentId() <= 0) {
            throw new IllegalArgumentException("Document event must contain a positive documentId");
        }
        if (event.userId() == null || event.userId() <= 0) {
            throw new IllegalArgumentException("Document event must contain a positive userId");
        }
        if (event.type() == null || event.type().isBlank()) {
            throw new IllegalArgumentException("Document event must contain a document type");
        }
        if (event.storagePath() == null || event.storagePath().isBlank()) {
            throw new IllegalArgumentException("Document event must contain a storage path");
        }
    }
}
