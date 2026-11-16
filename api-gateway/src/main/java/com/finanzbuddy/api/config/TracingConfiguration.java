package com.finanzbuddy.api.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationView;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.exporter.SpanExportingPredicate;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Tracing settings beyond application.yml (root capability homebuddy-tracing).
 *
 * <p>Requests to {@code /actuator/**} (health checks every 10 s, scrapes every 15 s) and runs of
 * {@code @Scheduled} jobs (the outbox poll every 5 s) would each create a trace. Their spans are
 * dropped before export; the observations still run, so HTTP and task metrics are unchanged. The
 * Spring Security observations inside an actuator request are skipped, because their spans would
 * otherwise be exported without their parent. The outbox publish span is not a child of the
 * scheduled run (see {@code OutboxTraceContext}), so it is exported.
 */
@Configuration(proxyBeanMethods = false)
class TracingConfiguration {

    private static final String ACTUATOR = "/actuator";

    @Bean
    SpanExportingPredicate skipActuatorAndScheduledTaskSpans() {
        return span -> !isActuatorRequest(span) && !isScheduledTaskRun(span);
    }

    @Bean
    ObservationPredicate skipSecurityObservationsOfActuatorRequests() {
        return (name, context) ->
                !(name.startsWith("spring.security.") && insideActuatorRequest(context));
    }

    static boolean isActuatorRequest(FinishedSpan span) {
        String uri = span.getTags().get("uri");
        return uri != null && uri.startsWith(ACTUATOR);
    }

    /** Spring names a scheduled run {@code task <bean>.<method>} and tags it with code.*. */
    static boolean isScheduledTaskRun(FinishedSpan span) {
        Map<String, String> tags = span.getTags();
        return span.getName().startsWith("task ")
                && tags.containsKey("code.function")
                && tags.containsKey("code.namespace");
    }

    static boolean insideActuatorRequest(Observation.ContextView context) {
        for (ObservationView parent = context.getParentObservation();
                parent != null;
                parent = parent.getContextView().getParentObservation()) {
            if (parent.getContextView() instanceof ServerRequestObservationContext server) {
                return server.getCarrier().getRequestURI().startsWith(ACTUATOR);
            }
        }
        return false;
    }
}
