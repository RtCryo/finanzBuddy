package com.finanzbuddy.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.exporter.SpanExportingPredicate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TracingConfigurationTest {

    private final SpanExportingPredicate predicate =
            new TracingConfiguration().skipActuatorAndScheduledTaskSpans();

    private static FinishedSpan span(String name, Map<String, String> tags) {
        FinishedSpan span = mock(FinishedSpan.class);
        when(span.getName()).thenReturn(name);
        when(span.getTags()).thenReturn(tags);
        return span;
    }

    @Test
    void dropsActuatorRequests() {
        assertThat(
                        predicate.isExportable(
                                span(
                                        "http get /actuator/health/{*path}",
                                        Map.of("uri", "/actuator/health/{*path}"))))
                .isFalse();
    }

    @Test
    void dropsScheduledTaskRuns() {
        assertThat(
                        predicate.isExportable(
                                span(
                                        "task outboxRelay.publishPending",
                                        Map.of(
                                                "code.function", "publishPending",
                                                "code.namespace",
                                                        "com.finanzbuddy.api.document.outbox.OutboxRelay"))))
                .isFalse();
    }

    @Test
    void keepsApiRequestsAndOutboxPublishes() {
        assertThat(
                        predicate.isExportable(
                                span("http post /documents", Map.of("uri", "/documents"))))
                .isTrue();
        assertThat(
                        predicate.isExportable(
                                span(
                                        "outbox publish",
                                        Map.of("messaging.destination.name", "documents.csv"))))
                .isTrue();
    }
}
