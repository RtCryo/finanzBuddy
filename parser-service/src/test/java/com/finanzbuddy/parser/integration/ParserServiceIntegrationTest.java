package com.finanzbuddy.parser.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.finanzbuddy.parser.config.IntegrationTestConfig;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.processing.adapter.out.persistence.TransactionEntity;
import com.finanzbuddy.parser.processing.adapter.out.persistence.TransactionRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"documents.csv", "documents.csv.dlt"})
@Import(IntegrationTestConfig.class)
@ActiveProfiles("test")
class ParserServiceIntegrationTest {

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.kafka.bootstrap-servers",
                () -> System.getProperty("spring.embedded.kafka.brokers"));
    }

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired private TransactionRepository transactionRepository;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        mockServer = IntegrationTestConfig.getMockServer();
        mockServer.reset();
    }

    @Test
    void endToEndCsvProcessingUpdatesDocumentStatus() {
        Long documentId = 42L;
        Long userId = 7L;

        mockServer
                .expect(requestTo("http://localhost:9999/internal/documents/42/status"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Internal-Api-Key", "test-key"))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andRespond(withSuccess());

        mockServer
                .expect(requestTo("http://localhost:9999/internal/documents/42/status"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Internal-Api-Key", "test-key"))
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andRespond(withSuccess());

        DocumentUploadedEvent event =
                new DocumentUploadedEvent(documentId, userId, "CSV", "csv/42");
        try {
            kafkaTemplate.send("documents.csv", event).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(
                        () -> {
                            List<TransactionEntity> transactions =
                                    transactionRepository.findByDocumentId(documentId);
                            assertThat(transactions).hasSize(2);
                            assertThat(transactions).allMatch(tx -> tx.getUserId().equals(userId));
                            assertThat(transactions.get(0).getRawData()).isNotBlank();
                        });

        mockServer.verify();
    }

    @Test
    void permanentProcessingFailureIsReportedAndSentToDltWithoutRetries() {
        Long documentId = 43L;
        mockServer
                .expect(requestTo("http://localhost:9999/internal/documents/43/status"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andRespond(withSuccess());
        mockServer
                .expect(requestTo("http://localhost:9999/internal/documents/43/status"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andRespond(withSuccess());

        Map<String, Object> consumerProperties =
                KafkaTestUtils.consumerProps("parser-dlt-test", "false", embeddedKafkaBroker);
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, byte[]> consumer =
                new org.springframework.kafka.core.DefaultKafkaConsumerFactory<>(
                                consumerProperties,
                                new StringDeserializer(),
                                new ByteArrayDeserializer())
                        .createConsumer()) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "documents.csv.dlt");
            DocumentUploadedEvent event =
                    new DocumentUploadedEvent(documentId, 7L, "UNKNOWN", "csv/43");
            try {
                kafkaTemplate.send("documents.csv", event).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            await().atMost(10, TimeUnit.SECONDS)
                    .pollInterval(Duration.ofMillis(200))
                    .untilAsserted(
                            () ->
                                    assertThat(
                                                    KafkaTestUtils.getRecords(
                                                                    consumer,
                                                                    Duration.ofMillis(200))
                                                            .records("documents.csv.dlt"))
                                            .isNotEmpty());
        }

        mockServer.verify();
    }
}
