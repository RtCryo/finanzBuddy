package com.finanzbuddy.parser.service;

import com.finanzbuddy.parser.client.GatewayStatusClient;
import com.finanzbuddy.parser.document.DocumentStatus;
import com.finanzbuddy.parser.entity.Transaction;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.parser.DocumentParser;
import com.finanzbuddy.parser.parser.DocumentParserRouter;
import com.finanzbuddy.parser.repository.TransactionRepository;
import com.finanzbuddy.parser.storage.MinioStorageService;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessor.class);

    private final TransactionRepository transactionRepository;
    private final MinioStorageService minioStorageService;
    private final DocumentParserRouter parserRouter;
    private final GatewayStatusClient gatewayStatusClient;

    public DocumentProcessor(
            TransactionRepository transactionRepository,
            MinioStorageService minioStorageService,
            DocumentParserRouter parserRouter,
            GatewayStatusClient gatewayStatusClient) {
        this.transactionRepository = transactionRepository;
        this.minioStorageService = minioStorageService;
        this.parserRouter = parserRouter;
        this.gatewayStatusClient = gatewayStatusClient;
    }

    @Transactional
    public void process(DocumentUploadedEvent event) {
        logger.info(
                "Processing document: documentId={}, type={}", event.documentId(), event.type());

        if (transactionRepository.existsByDocumentId(event.documentId())) {
            logger.info("Duplicate event ignored for documentId={}", event.documentId());
            return;
        }

        reportStatus(event.documentId(), DocumentStatus.PROCESSING, null);

        try (InputStream inputStream = minioStorageService.download(event.storagePath())) {
            DocumentParser parser = parserRouter.route(event.type());
            List<Transaction> transactions = parser.parse(inputStream, event, resolveLocale(event));

            transactions.forEach(
                    transaction -> {
                        transaction.setDocumentId(event.documentId());
                        transaction.setUserId(event.userId());
                    });

            transactionRepository.saveAll(transactions);
            reportStatus(event.documentId(), DocumentStatus.PROCESSED, null);
            logger.info(
                    "Document processed successfully: documentId={}, transactions={}",
                    event.documentId(),
                    transactions.size());
        } catch (Exception e) {
            logger.error("Failed to process document: documentId={}", event.documentId(), e);
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.length() > 4000) {
                errorMessage = errorMessage.substring(0, 4000);
            }
            reportStatus(event.documentId(), DocumentStatus.FAILED, errorMessage);
        }
    }

    private Locale resolveLocale(DocumentUploadedEvent event) {
        return Locale.GERMANY;
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
