package com.finanzbuddy.parser.config;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Removes exception details from spans before they leave the component (homebuddy-tracing).
 *
 * <p>Exception messages and stack traces can carry document content: a parser error, for example,
 * quotes the CSV value it could not read. Every exported span therefore loses its status
 * description, and exception events keep only {@code exception.type} and {@code exception.escaped};
 * the message and the stack trace, including nested causes, are dropped. The full error stays in
 * the component's log, which links to the trace by its id.
 */
public final class SanitizingSpanExporter implements SpanExporter {

    static final String EXCEPTION_EVENT = "exception";
    private static final Set<String> KEPT_EXCEPTION_ATTRIBUTES =
            Set.of("exception.type", "exception.escaped");
    private static final Set<String> DROPPED_SPAN_ATTRIBUTES =
            Set.of("error", "exception.message", "exception.stacktrace");

    private final SpanExporter delegate;

    public SanitizingSpanExporter(SpanExporter delegate) {
        this.delegate = delegate;
    }

    public SpanExporter delegate() {
        return delegate;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        return delegate.export(spans.stream().map(SanitizingSpanExporter::sanitize).toList());
    }

    @Override
    public CompletableResultCode flush() {
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }

    static SpanData sanitize(SpanData span) {
        StatusData status = span.getStatus();
        StatusData sanitizedStatus = StatusData.create(status.getStatusCode(), "");
        List<EventData> events =
                span.getEvents().stream().map(SanitizingSpanExporter::sanitize).toList();
        Attributes attributes = without(span.getAttributes(), DROPPED_SPAN_ATTRIBUTES::contains);
        return new DelegatingSpanData(span) {
            @Override
            public StatusData getStatus() {
                return sanitizedStatus;
            }

            @Override
            public List<EventData> getEvents() {
                return events;
            }

            @Override
            public Attributes getAttributes() {
                return attributes;
            }
        };
    }

    private static EventData sanitize(EventData event) {
        if (!EXCEPTION_EVENT.equals(event.getName())) {
            return event;
        }
        Attributes kept =
                without(event.getAttributes(), key -> !KEPT_EXCEPTION_ATTRIBUTES.contains(key));
        return EventData.create(event.getEpochNanos(), event.getName(), kept, kept.size());
    }

    private static Attributes without(
            Attributes attributes, java.util.function.Predicate<String> drop) {
        return attributes.toBuilder()
                .removeIf((AttributeKey<?> key) -> drop.test(key.getKey()))
                .build();
    }
}
