package com.finanzbuddy.parser.processing.port.in;

import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.processing.domain.model.ProcessingOutcome;

/** Inbound port: handle one document upload event end to end. */
public interface ProcessDocumentUseCase {

    ProcessingOutcome process(DocumentUploadedEvent event);
}
