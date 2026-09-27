package com.finanzbuddy.parser.event;

import static org.mockito.Mockito.verify;

import com.finanzbuddy.parser.service.DocumentProcessor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DocumentEventListenersTest {

    private final DocumentProcessor documentProcessor = Mockito.mock(DocumentProcessor.class);
    private final DocumentEventListeners listeners = new DocumentEventListeners(documentProcessor);

    @Test
    void delegatesCsvEventToProcessor() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");

        listeners.onCsvDocument(event);

        verify(documentProcessor).process(event);
    }

    @Test
    void delegatesPdfEventToProcessor() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "PDF", "pdf/1");

        listeners.onPdfDocument(event);

        verify(documentProcessor).process(event);
    }

    @Test
    void delegatesXmlEventToProcessor() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "XML", "xml/1");

        listeners.onXmlDocument(event);

        verify(documentProcessor).process(event);
    }
}
