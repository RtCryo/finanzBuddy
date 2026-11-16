package com.finanzbuddy.api.document.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.finanzbuddy.api.entity.OutboxEvent;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OutboxTraceContextTest {

    private final SdkTracerProvider provider = SdkTracerProvider.builder().build();
    private final OpenTelemetrySdk openTelemetry =
            OpenTelemetrySdk.builder()
                    .setTracerProvider(provider)
                    .setPropagators(
                            ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                    .build();
    private final io.opentelemetry.api.trace.Tracer otelTracer = openTelemetry.getTracer("test");
    private final Tracer tracer =
            new OtelTracer(otelTracer, new OtelCurrentTraceContext(), event -> {});
    private final OutboxTraceContext traceContext =
            new OutboxTraceContext(
                    tracer, new OtelPropagator(openTelemetry.getPropagators(), otelTracer));

    @AfterEach
    void close() {
        provider.close();
    }

    @Test
    void storesTheTraceparentOfTheCurrentSpan() {
        Span request = tracer.nextSpan().name("request").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(request)) {
            // Flags: sampled, plus the W3C random-trace-id flag OpenTelemetry sets.
            assertThat(traceContext.currentTraceParent())
                    .matches(
                            "00-"
                                    + request.context().traceId()
                                    + "-"
                                    + request.context().spanId()
                                    + "-0[13]");
        } finally {
            request.end();
        }
    }

    @Test
    void storesNothingOutsideATrace() {
        assertThat(traceContext.currentTraceParent()).isNull();
    }

    @Test
    void publishSpanContinuesTheStoredTrace() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String requestSpanId = "00f067aa0ba902b7";
        OutboxEvent event =
                new OutboxEvent(
                        1L, "documents.csv", "{}", "00-" + traceId + "-" + requestSpanId + "-01");

        Span publish = traceContext.startPublishSpan(event);
        publish.end();

        assertThat(publish.context().traceId()).isEqualTo(traceId);
        assertThat(publish.context().parentId()).isEqualTo(requestSpanId);
        assertThat(publish.context().sampled()).isTrue();
    }

    @Test
    void publishSpanFollowsAnUnsampledUpload() {
        OutboxEvent event =
                new OutboxEvent(
                        1L,
                        "documents.csv",
                        "{}",
                        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00");

        Span publish = traceContext.startPublishSpan(event);
        publish.end();

        assertThat(publish.context().sampled()).isFalse();
    }

    @Test
    void eventWithoutContextStartsANewTraceEvenInsideAnotherSpan() {
        Span poll = tracer.nextSpan().name("scheduled poll").start();
        Span publish;
        try (Tracer.SpanInScope ignored = tracer.withSpan(poll)) {
            publish = traceContext.startPublishSpan(new OutboxEvent(1L, "documents.csv", "{}"));
            publish.end();
        } finally {
            poll.end();
        }

        assertThat(publish.context().traceId()).isNotEqualTo(poll.context().traceId());
        assertThat(publish.context().parentId()).isNull();
    }
}
