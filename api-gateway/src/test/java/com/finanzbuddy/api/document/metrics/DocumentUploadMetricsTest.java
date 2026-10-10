package com.finanzbuddy.api.document.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class DocumentUploadMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final DocumentUploadMetrics metrics = new DocumentUploadMetrics(registry);

    @Test
    void normalizesSupportedAndUnknownTypesToBoundedLabels() {
        metrics.recordSuccess("CSV");
        metrics.recordSuccess("PDF");
        metrics.recordSuccess("XML");
        metrics.recordSuccess(" ");
        metrics.recordFailure(null);
        metrics.recordFailure("TXT");

        assertThat(uploadCount("CSV", "success")).isEqualTo(1.0);
        assertThat(uploadCount("PDF", "success")).isEqualTo(1.0);
        assertThat(uploadCount("XML", "success")).isEqualTo(1.0);
        assertThat(uploadCount("OTHER", "success")).isEqualTo(1.0);
        assertThat(uploadCount("OTHER", "failure")).isEqualTo(2.0);
        assertThat(
                        registry.get("api.documents.uploads")
                                .tag("type", "OTHER")
                                .tag("outcome", "failure")
                                .counter()
                                .getId()
                                .getTags())
                .containsExactlyInAnyOrder(Tag.of("type", "OTHER"), Tag.of("outcome", "failure"));
    }

    private double uploadCount(String type, String outcome) {
        return registry.get("api.documents.uploads")
                .tag("type", type)
                .tag("outcome", outcome)
                .counter()
                .count();
    }
}
