package com.finanzbuddy.parser.event;

import com.finanzbuddy.parser.service.DocumentProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DocumentEventListeners {

    private static final Logger logger = LoggerFactory.getLogger(DocumentEventListeners.class);

    private final DocumentProcessor documentProcessor;

    public DocumentEventListeners(DocumentProcessor documentProcessor) {
        this.documentProcessor = documentProcessor;
    }

    @KafkaListener(topics = "documents.csv", autoStartup = "${parser.formats.csv:true}")
    public void onCsvDocument(DocumentUploadedEvent event) {
        logger.info("Received CSV document event: documentId={}", event.documentId());
        documentProcessor.process(event);
    }

    @KafkaListener(topics = "documents.pdf", autoStartup = "${parser.formats.pdf:false}")
    public void onPdfDocument(DocumentUploadedEvent event) {
        logger.info("Received PDF document event: documentId={}", event.documentId());
        documentProcessor.process(event);
    }

    @KafkaListener(topics = "documents.xml", autoStartup = "${parser.formats.xml:false}")
    public void onXmlDocument(DocumentUploadedEvent event) {
        logger.info("Received XML document event: documentId={}", event.documentId());
        documentProcessor.process(event);
    }
}
