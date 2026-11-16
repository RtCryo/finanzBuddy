package com.finanzbuddy.parser.config;

import io.micrometer.tracing.exporter.SpanExportingPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tracing settings beyond application.yml (root capability homebuddy-tracing).
 *
 * <p>Health checks and Prometheus scrapes would create a trace every few seconds. Their spans are
 * not exported; the observations still run, so the HTTP metrics are unchanged. parser-service has
 * no Spring Security, so an actuator request has no child spans.
 */
@Configuration(proxyBeanMethods = false)
class TracingConfiguration {

    @Bean
    SpanExportingPredicate skipActuatorSpans() {
        return span -> {
            String uri = span.getTags().get("uri");
            return uri == null || !uri.startsWith("/actuator");
        };
    }
}
