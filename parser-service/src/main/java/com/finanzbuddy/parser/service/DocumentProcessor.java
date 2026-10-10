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
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class DocumentProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessor.class);
    private static final List<String> METRIC_DOCUMENT_TYPES = List.of("CSV", "PDF", "XML", "OTHER");
    private static final Map<String, String> DOCUMENT_COUNTER_DESCRIPTIONS =
            Map.of(
                    "parser.documents.processed",
                    "Number of successfully processed documents by type",
                    "parser.documents.failed",
                    "Number of failed document processing attempts by type",
                    "parser.documents.duplicate",
                    "Number of duplicate document events ignored by type");

    private final TransactionPersistenceService transactionPersistenceService;
    private final MinioStorageService minioStorageService;
    private final DocumentParserRouter parserRouter;
    private final GatewayStatusClient gatewayStatusClient;
    private final MeterRegistry meterRegistry;
    private final Locale defaultLocale;

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
        for (String type : METRIC_DOCUMENT_TYPES) {
            documentCounter("parser.documents.processed", type);
            documentCounter("parser.documents.failed", type);
            documentCounter("parser.documents.duplicate", type);
            Counter.builder("parser.transactions.parsed")
                    .description("Total number of transactions parsed from successful documents")
                    .tag("type", type)
                    .register(meterRegistry);
            processingTimer(type);
        }
    }

    public void process(DocumentUploadedEvent event) {
        validate(event);
        String type = metricDocumentType(event.type());
        logger.info(
                "Processing document: documentId={}, type={}", event.documentId(), event.type());

        Timer.Sample sample = Timer.start(meterRegistry);
        Timer processingTimer = processingTimer(type);
        try {
            if (transactionPersistenceService.isProcessed(event.documentId())) {
                logger.info("Duplicate event ignored for documentId={}", event.documentId());
                reportStatus(event.documentId(), DocumentStatus.PROCESSED, null);
                documentCounter("parser.documents.duplicate", type).increment();
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
                Counter.builder("parser.transactions.parsed")
                        .description(
                                "Total number of transactions parsed from successful documents")
                        .tag("type", type)
                        .register(meterRegistry)
                        .increment(transactions.size());
                reportStatus(event.documentId(), DocumentStatus.PROCESSED, null);
                documentCounter("parser.documents.processed", type).increment();
                logger.info(
                        "Document processed successfully: documentId={}, transactions={}",
                        event.documentId(),
                        transactions.size());
            }
        } catch (DuplicateDocumentException e) {
            logger.info("Duplicate event ignored for documentId={}", event.documentId());
            reportStatus(event.documentId(), DocumentStatus.PROCESSED, null);
            documentCounter("parser.documents.duplicate", type).increment();
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            documentCounter("parser.documents.failed", type).increment();
            throw e;
        } catch (StorageException | DataAccessException e) {
            logger.error(
                    "Transient failure processing document: documentId={}", event.documentId(), e);
            documentCounter("parser.documents.failed", type).increment();
            throw e;
        } catch (Exception e) {
            logger.error(
                    "Unexpected failure processing document: documentId={}", event.documentId(), e);
            documentCounter("parser.documents.failed", type).increment();
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
        gatewayStatusClient.reportStatus(documentId, status, message);
    }

    private Counter documentCounter(String name, String type) {
        return Counter.builder(name)
                .description(DOCUMENT_COUNTER_DESCRIPTIONS.get(name))
                .tag("type", type)
                .register(meterRegistry);
    }

    private Timer processingTimer(String type) {
        return Timer.builder("parser.documents.processing.duration")
                .description("Document processing duration by document type")
                .tag("type", type)
                .register(meterRegistry);
    }

    private String metricDocumentType(String type) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "CSV", "PDF", "XML" -> type.toUpperCase(Locale.ROOT);
            default -> "OTHER";
        };
    }
}
