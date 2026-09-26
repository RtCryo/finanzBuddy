package com.finanzbuddy.parser.event;

import com.finanzbuddy.parser.service.DocumentProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka listeners for document events. Method-level {@code @ConditionalOnProperty} is used
 * intentionally so that parser formats can be toggled independently without creating separate
 * listener beans for each format.
 */
@Component
public class DocumentEventListeners {

    private static final Logger logger = LoggerFactory.getLogger(DocumentEventListeners.class);

    private final DocumentProcessor documentProcessor;

    public DocumentEventListeners(DocumentProcessor documentProcessor) {
        this.documentProcessor = documentProcessor;
    }

    @ConditionalOnProperty(name = "parser.formats.csv", havingValue = "true")
    @KafkaListener(topics = "documents.csv")
    public void onCsvDocument(DocumentUploadedEvent event) {
        logger.info("Received CSV document event: documentId={}", event.documentId());
        documentProcessor.process(event);
    }

    @ConditionalOnProperty(name = "parser.formats.pdf", havingValue = "true")
    @KafkaListener(topics = "documents.pdf")
    public void onPdfDocument(DocumentUploadedEvent event) {
        logger.info("Received PDF document event: documentId={}", event.documentId());
        documentProcessor.process(event);
    }

    @ConditionalOnProperty(name = "parser.formats.xml", havingValue = "true")
    @KafkaListener(topics = "documents.xml")
    public void onXmlDocument(DocumentUploadedEvent event) {
        logger.info("Received XML document event: documentId={}", event.documentId());
        documentProcessor.process(event);
    }
}
