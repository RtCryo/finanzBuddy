package com.finanzbuddy.parser.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.finanzbuddy.parser.config.IntegrationTestConfig;
import com.finanzbuddy.parser.entity.Transaction;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.repository.TransactionRepository;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = { "documents.csv" })
@Import(IntegrationTestConfig.class)
@ActiveProfiles("test")
@DirtiesContext
class ParserServiceIntegrationTest {

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
    }

    @Autowired
    private KafkaTemplate<String, DocumentUploadedEvent> kafkaTemplate;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    void endToEndCsvProcessingUpdatesDocumentStatus() {
        Long documentId = 42L;
        Long userId = 7L;

        mockServer.expect(requestTo("http://localhost:9999/internal/documents/42/status"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Internal-Api-Key", "test-key"))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andRespond(withSuccess());

        mockServer.expect(requestTo("http://localhost:9999/internal/documents/42/status"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Internal-Api-Key", "test-key"))
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andRespond(withSuccess());

        DocumentUploadedEvent event = new DocumentUploadedEvent(documentId, userId, "CSV", "csv/42");
        try {
            kafkaTemplate.send("documents.csv", event).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        await().atMost(15, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            List<Transaction> transactions = transactionRepository.findByDocumentId(documentId);
            assertThat(transactions).hasSize(2);
            assertThat(transactions).allMatch(tx -> tx.getUserId().equals(userId));
            assertThat(transactions.get(0).getRawData()).isNotBlank();
        });

        mockServer.verify();
    }
}
