package com.finanzbuddy.parser.processing.port.out;

import com.finanzbuddy.parser.document.DocumentStatus;

/** Outbound port: report document processing status back to the gateway. */
public interface StatusReporterPort {

    void report(Long documentId, DocumentStatus status, String message);
}
