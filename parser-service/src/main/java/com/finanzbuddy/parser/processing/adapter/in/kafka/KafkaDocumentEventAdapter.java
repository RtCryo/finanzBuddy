package com.finanzbuddy.parser.processing.adapter.in.kafka;

import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.processing.port.in.ProcessDocumentUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaDocumentEventAdapter {

    private static final Logger logger = LoggerFactory.getLogger(KafkaDocumentEventAdapter.class);

    private final ProcessDocumentUseCase processDocument;

    public KafkaDocumentEventAdapter(ProcessDocumentUseCase processDocument) {
        this.processDocument = processDocument;
    }

    @KafkaListener(topics = "documents.csv", autoStartup = "${parser.formats.csv:true}")
    public void onCsvDocument(DocumentUploadedEvent event) {
        logger.info("Received CSV document event: documentId={}", event.documentId());
        processDocument.process(event);
    }

    @KafkaListener(topics = "documents.pdf", autoStartup = "${parser.formats.pdf:false}")
    public void onPdfDocument(DocumentUploadedEvent event) {
        logger.info("Received PDF document event: documentId={}", event.documentId());
        processDocument.process(event);
    }

    @KafkaListener(topics = "documents.xml", autoStartup = "${parser.formats.xml:false}")
    public void onXmlDocument(DocumentUploadedEvent event) {
        logger.info("Received XML document event: documentId={}", event.documentId());
        processDocument.process(event);
    }
}
