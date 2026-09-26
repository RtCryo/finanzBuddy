package com.finanzbuddy.parser.service;

import com.finanzbuddy.parser.client.GatewayStatusClient;
import com.finanzbuddy.parser.document.DocumentStatus;
import com.finanzbuddy.parser.entity.Transaction;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.parser.DocumentParser;
import com.finanzbuddy.parser.parser.DocumentParserRouter;
import com.finanzbuddy.parser.storage.MinioStorageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DocumentProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessor.class);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 4000;

    private final TransactionPersistenceService transactionPersistenceService;
    private final MinioStorageService minioStorageService;
    private final DocumentParserRouter parserRouter;
    private final GatewayStatusClient gatewayStatusClient;
    private final Locale defaultLocale;
    private final Counter processedCounter;
    private final Counter failedCounter;
    private final Counter duplicateCounter;
    private final Timer processingTimer;

    public DocumentProcessor(
            TransactionPersistenceService transactionPersistenceService,
            MinioStorageService minioStorageService,
            DocumentParserRouter parserRouter,
            GatewayStatusClient gatewayStatusClient,
            @Value("${parser.formats.csv-locale:de-DE}") Locale defaultLocale,
            MeterRegistry meterRegistry) {
        this.transactionPersistenceService = transactionPersistenceService;
        this.minioStorageService = minioStorageService;
        this.parserRouter = parserRouter;
        this.gatewayStatusClient = gatewayStatusClient;
        this.defaultLocale = defaultLocale;
        this.processedCounter =
                Counter.builder("parser.documents.processed")
                        .description("Number of successfully processed documents")
                        .register(meterRegistry);
        this.failedCounter =
                Counter.builder("parser.documents.failed")
                        .description("Number of failed document processing attempts")
                        .register(meterRegistry);
        this.duplicateCounter =
                Counter.builder("parser.documents.duplicate")
                        .description("Number of duplicate document events ignored")
                        .register(meterRegistry);
        this.processingTimer =
                Timer.builder("parser.documents.processing.duration")
                        .description("Document processing duration")
                        .register(meterRegistry);
    }

    public void process(DocumentUploadedEvent event) {
        logger.info(
                "Processing document: documentId={}, type={}", event.documentId(), event.type());

        if (transactionPersistenceService.isProcessed(event.documentId())) {
            logger.info("Duplicate event ignored for documentId={}", event.documentId());
            duplicateCounter.increment();
            return;
        }

        reportStatus(event.documentId(), DocumentStatus.PROCESSING, null);

        long start = System.nanoTime();
        try (InputStream inputStream = minioStorageService.download(event.storagePath())) {
            DocumentParser parser = parserRouter.route(event.type());
            List<Transaction> transactions = parser.parse(inputStream, event, resolveLocale(event));

            if (transactions.isEmpty()) {
                throw new IllegalArgumentException(
                        "No transactions found in document " + event.documentId());
            }

            transactionPersistenceService.saveIfNotProcessed(event.documentId(), transactions);
            reportStatus(event.documentId(), DocumentStatus.PROCESSED, null);
            processedCounter.increment();
            processingTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            logger.info(
                    "Document processed successfully: documentId={}, transactions={}",
                    event.documentId(),
                    transactions.size());
        } catch (DuplicateDocumentException e) {
            logger.info("Duplicate event ignored for documentId={}", event.documentId());
            duplicateCounter.increment();
        } catch (Exception e) {
            logger.error("Failed to process document: documentId={}", event.documentId(), e);
            failedCounter.increment();
            reportStatus(event.documentId(), DocumentStatus.FAILED, truncateErrorMessage(e));
        }
    }

    private Locale resolveLocale(DocumentUploadedEvent event) {
        // The event currently does not carry a locale; use the configured default.
        // If a locale is added to the event later, it can be preferred here.
        return defaultLocale;
    }

    private String truncateErrorMessage(Exception e) {
        String errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
        if (errorMessage.length() > MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
        }
        return errorMessage;
    }

    private void reportStatus(Long documentId, DocumentStatus status, String message) {
        try {
            gatewayStatusClient.reportStatus(documentId, status, message);
        } catch (Exception e) {
            logger.error(
                    "Failed to report status: documentId={}, status={}", documentId, status, e);
        }
    }
}
