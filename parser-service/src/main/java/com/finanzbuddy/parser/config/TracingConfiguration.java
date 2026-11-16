package com.finanzbuddy.parser.config;

import io.micrometer.tracing.exporter.SpanExportingPredicate;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.beans.factory.config.BeanPostProcessor;
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

    /**
     * Wraps every span exporter, so no exception message or stack trace leaves this component (see
     * {@link SanitizingSpanExporter}). Static, because it post-processes other beans.
     */
    @Bean
    static BeanPostProcessor sanitizeExportedSpans() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof SpanExporter exporter
                                && !(bean instanceof SanitizingSpanExporter)
                        ? new SanitizingSpanExporter(exporter)
                        : bean;
            }
        };
    }

    @Bean
    SpanExportingPredicate skipActuatorSpans() {
        return span -> {
            String uri = span.getTags().get("uri");
            return uri == null || !uri.startsWith("/actuator");
        };
    }
}
