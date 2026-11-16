package com.finanzbuddy.parser.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.finanzbuddy.parser.config.IntegrationTestConfig;
import com.finanzbuddy.parser.config.RestClientConfig;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.storage.MinioStorageService;
import com.finanzbuddy.parser.storage.StorageException;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The parser continues the upload's trace from the Kafka record and propagates it in its status
 * callbacks (application-tracing, root homebuddy-tracing).
 */
// src/test/resources/application.yml replaces the main one in tests, so the tracing settings of
// the main application.yml (checked by TracingSettingsTest) are repeated here.
@SpringBootTest(
        properties = {
            "management.tracing.sampling.probability=1.0",
            "management.otlp.metrics.export.enabled=false",
            "management.logging.export.otlp.enabled=false",
            "management.opentelemetry.tracing.export.schedule-delay=10ms"
        })
@EmbeddedKafka(
        partitions = 1,
        topics = {"documents.csv", "documents.csv.dlt"})
@Import(ParserTracingIntegrationTest.TracingTestConfig.class)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ParserTracingIntegrationTest {

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.kafka.bootstrap-servers",
                () -> System.getProperty("spring.embedded.kafka.brokers"));
    }

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private Tracer tracer;
    @Autowired private ApplicationContext context;

    @Test
    void nothingIsExportedOverOtlpWithoutAnEndpoint() {
        assertThat(context.getBeansOfType(OtlpHttpSpanExporter.class)).isEmpty();
        assertThat(context.getBeansOfType(OtlpMeterRegistry.class)).isEmpty();
    }

    @Test
    void consumedRecordContinuesTheTraceIntoTheStatusCallbacks(CapturedOutput output)
            throws Exception {
        MockRestServiceServer server = TracingTestConfig.mockServer;
        Span upload = tracer.nextSpan().name("upload").start();
        String traceparent = "00-" + upload.context().traceId() + "-[0-9a-f]{16}-0[13]";
        for (String status : new String[] {"PROCESSING", "PROCESSED"}) {
            server.expect(requestTo("http://localhost:9999/internal/documents/77/status"))
                    .andExpect(method(HttpMethod.PUT))
                    .andExpect(header("X-Internal-Api-Key", "test-key"))
                    .andExpect(header("traceparent", matchesPattern(traceparent)))
                    .andExpect(jsonPath("$.status").value(status))
                    .andRespond(withSuccess());
        }

        // The observed template adds a producer span of the upload's trace and the record header.
        try (Tracer.SpanInScope ignored = tracer.withSpan(upload)) {
            kafkaTemplate
                    .send("documents.csv", new DocumentUploadedEvent(77L, 7L, "CSV", "csv/77"))
                    .get(5, TimeUnit.SECONDS);
        } finally {
            upload.end();
        }

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(server::verify);
        assertThat(output.getOut())
                .containsPattern(
                        Pattern.quote("[" + upload.context().traceId() + "-")
                                + "[0-9a-f]{16}\\].*Received CSV document event: documentId=77");
    }

    @Test
    void failedStatementLeavesNoDocumentContentInSpans() throws Exception {
        MockRestServiceServer server = TracingTestConfig.mockServer;
        server.reset();
        server.expect(
                        ExpectedCount.manyTimes(),
                        requestTo("http://localhost:9999/internal/documents/78/status"))
                .andRespond(withSuccess());
        Span upload = tracer.nextSpan().name("upload").start();
        String traceId = upload.context().traceId();

        try (Tracer.SpanInScope ignored = tracer.withSpan(upload)) {
            kafkaTemplate
                    .send(
                            "documents.csv",
                            new DocumentUploadedEvent(78L, 7L, "CSV", TracingTestConfig.BAD_PATH))
                    .get(5, TimeUnit.SECONDS);
        } finally {
            upload.end();
        }

        // The parser fails on the amount, and its consumer span is exported with an error status.
        await().atMost(20, TimeUnit.SECONDS)
                .until(
                        () ->
                                TracingTestConfig.SPANS.stream()
                                        .anyMatch(
                                                s ->
                                                        s.getTraceId().equals(traceId)
                                                                && s.getStatus().getStatusCode()
                                                                        == StatusCode.ERROR));
        List<SpanData> spans =
                TracingTestConfig.SPANS.stream()
                        .filter(s -> s.getTraceId().equals(traceId))
                        .toList();
        assertThat(spans)
                .anySatisfy(
                        span ->
                                assertThat(span.getEvents())
                                        .anySatisfy(
                                                event ->
                                                        assertThat(
                                                                        event.getAttributes()
                                                                                .get(
                                                                                        AttributeKey
                                                                                                .stringKey(
                                                                                                        "exception.type")))
                                                                .isNotBlank()));
        for (SpanData span : spans) {
            assertThat(span.getStatus().getDescription()).isEmpty();
            assertThat(span.getAttributes().toString()).doesNotContain(TracingTestConfig.SECRET);
            for (EventData event : span.getEvents()) {
                assertThat(event.getAttributes().toString())
                        .doesNotContain(TracingTestConfig.SECRET);
                assertThat(event.getAttributes().get(AttributeKey.stringKey("exception.message")))
                        .isNull();
                assertThat(
                                event.getAttributes()
                                        .get(AttributeKey.stringKey("exception.stacktrace")))
                        .isNull();
            }
        }
    }

    @TestConfiguration
    static class TracingTestConfig {

        static MockRestServiceServer mockServer;

        /** A marker that only appears in the bad statement's amount column. */
        static final String SECRET = "SECRET-AMOUNT-4711";

        static final String BAD_PATH = "csv/bad-78";
        static final String BAD_CSV =
                "BookingDate;ValueDate;Amount;Currency;Counterparty;Description;Category\n"
                        + "10.01.24;10.01.24;"
                        + SECRET
                        + ";EUR;Coffee Shop;Morning coffee;Food";

        /** Spans as they leave the parser, after SanitizingSpanExporter. */
        static final List<SpanData> SPANS = new CopyOnWriteArrayList<>();

        @Bean
        SpanExporter collectingSpanExporter() {
            return new SpanExporter() {
                @Override
                public CompletableResultCode export(Collection<SpanData> batch) {
                    SPANS.addAll(batch);
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
            };
        }

        /** The production builder from RestClientConfig, bound to a mock gateway. */
        @Bean
        @Primary
        RestClient.Builder tracedRestClientBuilder(
                ObjectProvider<ObservationRegistry> observationRegistry) {
            RestClient.Builder builder =
                    new RestClientConfig()
                            .restClientBuilder(
                                    Duration.ofSeconds(5),
                                    Duration.ofSeconds(30),
                                    observationRegistry);
            mockServer = MockRestServiceServer.bindTo(builder).build();
            return builder;
        }

        @Bean
        @Primary
        MinioStorageService testMinioStorageService() {
            return new MinioStorageService(null, null) {
                @Override
                public InputStream download(String storagePath) {
                    if (storagePath == null || !storagePath.startsWith("csv/")) {
                        throw new StorageException("Unexpected storage path: " + storagePath, null);
                    }
                    String csv =
                            BAD_PATH.equals(storagePath)
                                    ? BAD_CSV
                                    : IntegrationTestConfig.SAMPLE_CSV;
                    return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
                }
            };
        }
    }
}
