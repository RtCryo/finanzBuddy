package com.finanzbuddy.parser.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

/**
 * The tracing settings of the main application.yml, which src/test/resources/application.yml
 * replaces in Spring tests (application-tracing, root homebuddy-tracing).
 */
class TracingSettingsTest {

    private static PropertySource<?> mainApplicationYaml() throws Exception {
        List<PropertySource<?>> documents =
                new YamlPropertySourceLoader()
                        .load("main", new FileSystemResource("src/main/resources/application.yml"));
        assertThat(documents).hasSize(1);
        return documents.getFirst();
    }

    @Test
    void samplesEverythingWithTheServiceResourceAndExportsOnlyTraces() throws Exception {
        PropertySource<?> yaml = mainApplicationYaml();

        assertThat(String.valueOf(yaml.getProperty("management.tracing.sampling.probability")))
                .isEqualTo("1.0");
        assertThat(
                        String.valueOf(
                                yaml.getProperty(
                                        "management.opentelemetry.resource-attributes.service.name")))
                .isEqualTo("finanzbuddy-parser-service");
        assertThat(
                        String.valueOf(
                                yaml.getProperty(
                                        "management.opentelemetry.resource-attributes.service.namespace")))
                .isEqualTo("finanzbuddy");
        assertThat(String.valueOf(yaml.getProperty("management.otlp.metrics.export.enabled")))
                .isEqualTo("false");
        assertThat(String.valueOf(yaml.getProperty("management.logging.export.otlp.enabled")))
                .isEqualTo("false");
        // The endpoint comes only from Compose, so tests and host runs export nothing.
        assertThat(yaml.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"))
                .isNull();
    }
}
