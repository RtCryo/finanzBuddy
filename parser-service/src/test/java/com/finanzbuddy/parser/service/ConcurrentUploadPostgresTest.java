package com.finanzbuddy.parser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.finanzbuddy.parser.entity.Transaction;
import com.finanzbuddy.parser.repository.ProcessedDocumentRepository;
import com.finanzbuddy.parser.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Proves the per-owner advisory lock against a real PostgreSQL. Runs only when the environment
 * variable PARSER_TEST_POSTGRES_URL points to an empty database, for example {@code
 * jdbc:postgresql://localhost:5432/parser_test}; user and password come from
 * PARSER_TEST_POSTGRES_USER and PARSER_TEST_POSTGRES_PASSWORD. Skipped otherwise.
 */
@SpringBootTest(properties = "parser.formats.csv=false")
@EmbeddedKafka(
        partitions = 1,
        topics = {"documents.csv", "documents.csv.dlt"})
@ActiveProfiles("test")
class ConcurrentUploadPostgresTest {

    private static final String URL = System.getenv("PARSER_TEST_POSTGRES_URL");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (URL == null) {
            return;
        }
        registry.add("spring.datasource.url", () -> URL);
        registry.add(
                "spring.datasource.username",
                () -> System.getenv().getOrDefault("PARSER_TEST_POSTGRES_USER", "postgres"));
        registry.add(
                "spring.datasource.password",
                () -> System.getenv().getOrDefault("PARSER_TEST_POSTGRES_PASSWORD", ""));
    }

    @Autowired private TransactionPersistenceService service;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ProcessedDocumentRepository processedDocumentRepository;

    @AfterEach
    void cleanUp() {
        if (URL != null) {
            transactionRepository.deleteAll();
            processedDocumentRepository.deleteAll();
        }
    }

    @Test
    void twoSimultaneousUploadsOfTheSameFileLeaveOneCopy() throws Exception {
        assumeTrue(URL != null, "PARSER_TEST_POSTGRES_URL not set");
        int uploads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(uploads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PersistenceOutcome>> results = new ArrayList<>();
        for (int i = 1; i <= uploads; i++) {
            long documentId = i;
            results.add(
                    pool.submit(
                            () -> {
                                start.await();
                                return service.saveIfNotProcessed(documentId, 1L, file(documentId));
                            }));
        }
        start.countDown();

        int saved = 0;
        int skipped = 0;
        for (Future<PersistenceOutcome> result : results) {
            PersistenceOutcome outcome = result.get(30, TimeUnit.SECONDS);
            saved += outcome.saved();
            skipped += outcome.skipped();
        }
        pool.shutdownNow();

        assertThat(saved).isEqualTo(3);
        assertThat(skipped).isEqualTo(3 * (uploads - 1));
        assertThat(transactionRepository.count()).isEqualTo(3);
    }

    private static List<Transaction> file(long documentId) {
        List<Transaction> lines = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Transaction t = new Transaction();
            t.setDocumentId(documentId);
            t.setUserId(1L);
            t.setBookingDate(LocalDate.of(2026, 8, 1).plusDays(i));
            t.setValueDate(t.getBookingDate());
            t.setAmount(new BigDecimal("-" + (i + 1)));
            t.setCurrency("EUR");
            t.setCounterparty("Shop " + i);
            t.setDescription("Line " + i);
            lines.add(t);
        }
        return lines;
    }
}
