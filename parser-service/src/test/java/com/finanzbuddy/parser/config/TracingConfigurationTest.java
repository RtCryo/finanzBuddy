package com.finanzbuddy.parser.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.exporter.SpanExportingPredicate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TracingConfigurationTest {

    private final SpanExportingPredicate predicate = new TracingConfiguration().skipActuatorSpans();

    private static FinishedSpan span(Map<String, String> tags) {
        FinishedSpan span = mock(FinishedSpan.class);
        when(span.getTags()).thenReturn(tags);
        return span;
    }

    @Test
    void dropsActuatorRequestsAndKeepsEverythingElse() {
        assertThat(predicate.isExportable(span(Map.of("uri", "/actuator/prometheus")))).isFalse();
        assertThat(predicate.isExportable(span(Map.of("uri", "/internal/documents/{id}/status"))))
                .isTrue();
        assertThat(
                        predicate.isExportable(
                                span(Map.of("messaging.destination.name", "documents.csv"))))
                .isTrue();
    }
}
