package com.finanzbuddy.api.document.metrics;

import com.finanzbuddy.api.document.DocumentType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DocumentUploadMetrics {

    private final MeterRegistry meterRegistry;

    public DocumentUploadMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        for (DocumentType type : DocumentType.values()) {
            uploadCounter(type.name(), "success");
            uploadCounter(type.name(), "failure");
        }
        uploadCounter("OTHER", "success");
        uploadCounter("OTHER", "failure");
    }

    public void recordSuccess(String type) {
        uploadCounter(type, "success").increment();
    }

    public void recordFailure(String type) {
        uploadCounter(type, "failure").increment();
    }

    private Counter uploadCounter(String type, String outcome) {
        return Counter.builder("api.documents.uploads")
                .description("Document upload attempts by type and outcome")
                .tag("type", normalizeType(type))
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "OTHER";
        }
        try {
            return DocumentType.valueOf(type.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException exception) {
            return "OTHER";
        }
    }
}
