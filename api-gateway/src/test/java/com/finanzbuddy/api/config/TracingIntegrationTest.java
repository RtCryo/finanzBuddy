package com.finanzbuddy.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Tracing setup of the API Gateway (application-tracing, root homebuddy-tracing). */
@SpringBootTest(
        properties = {
            // Export immediately, and let the outbox poll run several times during the test.
            "management.opentelemetry.tracing.export.schedule-delay=10ms",
            "app.outbox.poll-interval-ms=100"
        })
@ActiveProfiles("test")
@Import(TracingIntegrationTest.ExporterConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class TracingIntegrationTest {

    @Autowired ApplicationContext context;
    @Autowired Tracer tracer;
    @Autowired CollectingSpanExporter exported;

    @Test
    void nothingIsExportedOverOtlpWithoutAnEndpoint() {
        assertThat(context.getBeansOfType(OtlpHttpSpanExporter.class)).isEmpty();
        assertThat(context.getBeansOfType(OtlpMeterRegistry.class)).isEmpty();
    }

    @Test
    void scheduledPollsAreNotExportedButOtherSpansAre() {
        Span marker = tracer.nextSpan().name("marker").start();
        marker.end();

        // The outbox poll runs every 100 ms; by the time the marker is exported, several polls
        // would have been exported too.
        await().atMost(Duration.ofSeconds(10))
                .until(() -> exported.spans.stream().anyMatch(s -> s.getName().equals("marker")));
        await().pollDelay(Duration.ofMillis(500)).until(() -> true);
        assertThat(exported.spans).noneMatch(s -> s.getName().startsWith("task "));
        SpanData span =
                exported.spans.stream()
                        .filter(s -> s.getName().equals("marker"))
                        .findFirst()
                        .orElseThrow();
        assertThat(span.getResource().getAttribute(AttributeKey.stringKey("service.name")))
                .isEqualTo("finanzbuddy-api-gateway");
        assertThat(span.getResource().getAttribute(AttributeKey.stringKey("service.namespace")))
                .isEqualTo("finanzbuddy");
    }

    @Test
    void logLinesInsideASpanCarryTheTraceAndSpanId(CapturedOutput output) {
        Span span = tracer.nextSpan().name("log-correlation").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            LoggerFactory.getLogger(TracingIntegrationTest.class).info("inside a traced request");
        } finally {
            span.end();
        }

        String correlation = "[" + span.context().traceId() + "-" + span.context().spanId() + "]";
        assertThat(output.getOut())
                .containsPattern(Pattern.quote(correlation) + ".*inside a traced request");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExporterConfig {

        @Bean
        CollectingSpanExporter collectingSpanExporter() {
            return new CollectingSpanExporter();
        }
    }

    /** Keeps exported spans in memory instead of sending them anywhere. */
    static class CollectingSpanExporter implements SpanExporter {

        final List<SpanData> spans = new CopyOnWriteArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> batch) {
            spans.addAll(batch);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }
}
