package com.finanzbuddy.api.document.outbox;

import com.finanzbuddy.api.entity.OutboxEvent;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Carries the trace of an upload request through the outbox (homebuddy-tracing): the request stores
 * its W3C {@code traceparent} with the event, and the relay publishes the event in a span whose
 * parent is that context, so upload, Kafka publish, parsing, and status callback form one trace.
 * Only the trace and span id and the sampled flag are stored; no baggage.
 */
@Component
public class OutboxTraceContext {

    static final String SPAN_NAME = "outbox publish";
    private static final String TRACEPARENT = "traceparent";

    private final Tracer tracer;
    private final Propagator propagator;

    @Autowired
    public OutboxTraceContext(
            ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this(
                tracer.getIfAvailable(() -> Tracer.NOOP),
                propagator.getIfAvailable(() -> Propagator.NOOP));
    }

    public OutboxTraceContext(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    /** Tracing switched off, for tests that build the outbox classes directly. */
    public static OutboxTraceContext noop() {
        return new OutboxTraceContext(Tracer.NOOP, Propagator.NOOP);
    }

    /** The {@code traceparent} of the current span, or {@code null} outside a trace. */
    public String currentTraceParent() {
        TraceContext context = tracer.currentTraceContext().context();
        if (context == null) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(context, carrier, Map::put);
        return carrier.get(TRACEPARENT);
    }

    /**
     * Starts the span in which the relay publishes one event: a child of the stored request
     * context, or a new trace when the event has none.
     */
    public Span startPublishSpan(OutboxEvent event) {
        Span.Builder builder =
                event.getTraceParent() == null
                        ? tracer.spanBuilder().setNoParent()
                        : propagator.extract(Map.of(TRACEPARENT, event.getTraceParent()), Map::get);
        return builder.name(SPAN_NAME).tag("messaging.destination.name", event.getTopic()).start();
    }

    /** Makes the span current, so the Kafka producer span becomes its child. */
    public Tracer.SpanInScope withSpan(Span span) {
        return tracer.withSpan(span);
    }
}
