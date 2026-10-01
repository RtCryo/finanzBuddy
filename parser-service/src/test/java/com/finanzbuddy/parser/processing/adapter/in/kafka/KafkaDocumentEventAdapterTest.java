package com.finanzbuddy.parser.processing.adapter.in.kafka;

import static org.mockito.Mockito.verify;

import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.processing.port.in.ProcessDocumentUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class KafkaDocumentEventAdapterTest {

    private final ProcessDocumentUseCase processDocument =
            Mockito.mock(ProcessDocumentUseCase.class);
    private final KafkaDocumentEventAdapter adapter =
            new KafkaDocumentEventAdapter(processDocument);

    @Test
    void delegatesCsvEventToUseCase() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");

        adapter.onCsvDocument(event);

        verify(processDocument).process(event);
    }

    @Test
    void delegatesPdfEventToUseCase() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "PDF", "pdf/1");

        adapter.onPdfDocument(event);

        verify(processDocument).process(event);
    }

    @Test
    void delegatesXmlEventToUseCase() {
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "XML", "xml/1");

        adapter.onXmlDocument(event);

        verify(processDocument).process(event);
    }
}
