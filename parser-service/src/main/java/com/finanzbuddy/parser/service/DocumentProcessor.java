package com.finanzbuddy.parser.service;

import com.finanzbuddy.parser.client.GatewayStatusClient;
import com.finanzbuddy.parser.document.DocumentStatus;
import com.finanzbuddy.parser.entity.Transaction;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.parser.DocumentParser;
import com.finanzbuddy.parser.parser.DocumentParserRouter;
import com.finanzbuddy.parser.storage.MinioStorageService;
import com.finanzbuddy.parser.storage.StorageException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class DocumentProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessor.class);

    private final TransactionPersistenceService transactionPersistenceService;
    private final MinioStorageService minioStorageService;
    private final DocumentParserRouter parserRouter;
    private final GatewayStatusClient gatewayStatusClient;
    private final MeterRegistry meterRegistry;
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
        this.meterRegistry = meterRegistry;
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
        validate(event);
        logger.info(
                "Processing document: documentId={}, type={}", event.documentId(), event.type());

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            if (transactionPersistenceService.isProcessed(event.documentId())) {
                logger.info("Duplicate event ignored for documentId={}", event.documentId());
                duplicateCounter.increment();
                return;
            }

            reportStatus(event.documentId(), DocumentStatus.PROCESSING, null);
            try (InputStream inputStream = minioStorageService.download(event.storagePath())) {
                DocumentParser parser = parserRouter.route(event.type());
                List<Transaction> transactions = parser.parse(inputStream, event, defaultLocale);

                if (transactions.isEmpty()) {
                    throw new IllegalArgumentException(
                            "No transactions found in document " + event.documentId());
                }

                transactionPersistenceService.saveIfNotProcessed(event.documentId(), transactions);
                reportStatus(event.documentId(), DocumentStatus.PROCESSED, null);
                processedCounter.increment();
                logger.info(
                        "Document processed successfully: documentId={}, transactions={}",
                        event.documentId(),
                        transactions.size());
            }
        } catch (DuplicateDocumentException e) {
            logger.info("Duplicate event ignored for documentId={}", event.documentId());
            duplicateCounter.increment();
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            failedCounter.increment();
            throw e;
        } catch (StorageException | DataAccessException e) {
            logger.error(
                    "Transient failure processing document: documentId={}", event.documentId(), e);
            failedCounter.increment();
            throw e;
        } catch (Exception e) {
            logger.error(
                    "Unexpected failure processing document: documentId={}", event.documentId(), e);
            failedCounter.increment();
            throw new IllegalStateException("Failed to process document " + event.documentId(), e);
        } finally {
            sample.stop(processingTimer);
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

    private void reportStatus(Long documentId, DocumentStatus status, String message) {
        try {
            gatewayStatusClient.reportStatus(documentId, status, message);
        } catch (Exception e) {
            logger.error(
                    "Failed to report status: documentId={}, status={}", documentId, status, e);
        }
    }
}
