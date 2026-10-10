package com.finanzbuddy.api.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class TransactionReadRepositoryTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private TransactionReadRepository repository;

    @BeforeEach
    void setUp() {
        database =
                new EmbeddedDatabaseBuilder()
                        .setName("transaction-test-" + UUID.randomUUID())
                        .setType(EmbeddedDatabaseType.H2)
                        .build();
        jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.execute(
                "CREATE TABLE documents (id BIGINT PRIMARY KEY, user_id BIGINT, status VARCHAR(20))");
        jdbcTemplate.execute(
                "CREATE TABLE transactions (id BIGINT PRIMARY KEY, document_id BIGINT, user_id"
                        + " BIGINT, booking_date DATE, value_date DATE, amount DECIMAL(19,4),"
                        + " currency VARCHAR(3), counterparty VARCHAR(500), description CLOB,"
                        + " category VARCHAR(255))");
        repository = new TransactionReadRepository(jdbcTemplate);

        jdbcTemplate.update(
                "INSERT INTO documents VALUES (1, 7, 'PROCESSED'), (2, 7, 'PENDING'), (3, 8, 'PROCESSED')");
        insertTransaction(
                1, 1, 7, DateValue.of("2024-03-10"), "Coffee shop", "Card purchase", "Food");
        insertTransaction(
                2, 1, 7, DateValue.of("2024-03-20"), "ACME GmbH", "Invoice paid", "Income");
        insertTransaction(3, 1, 7, null, "No date", "Missing booking date", null);
        insertTransaction(
                4, 2, 7, DateValue.of("2024-03-15"), "Pending row", "Not processed", "Food");
        insertTransaction(
                5, 3, 8, DateValue.of("2024-03-15"), "Foreign row", "Other owner", "Food");
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void appliesCombinedFiltersCaseInsensitivelyAndKeepsDateBoundsInclusive() {
        Page<?> page =
                repository.findHistory(
                        7L,
                        "aCmE",
                        LocalDate.parse("2024-03-20"),
                        LocalDate.parse("2024-03-20"),
                        "Income",
                        PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
        assertThat(
                        ((com.finanzbuddy.api.document.dto.TransactionResponse)
                                        page.getContent().get(0))
                                .id())
                .isEqualTo(2L);
    }

    @Test
    void scopesToProcessedOwnerRowsSortsUndatedLastAndHonorsPageBoundaries() {
        Page<com.finanzbuddy.api.document.dto.TransactionResponse> first =
                repository.findHistory(7L, null, null, null, null, PageRequest.of(0, 2));
        Page<com.finanzbuddy.api.document.dto.TransactionResponse> last =
                repository.findHistory(7L, null, null, null, null, PageRequest.of(1, 2));

        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent()).extracting("id").containsExactly(2L, 1L);
        assertThat(last.getContent()).extracting("id").containsExactly(3L);
        assertThat(last.getContent().get(0).bookingDate()).isNull();
    }

    @Test
    void filtersUncategorizedRowsAndListsOnlyOwnersProcessedCategories() {
        Page<com.finanzbuddy.api.document.dto.TransactionResponse> uncategorized =
                repository.findHistory(
                        7L,
                        null,
                        null,
                        null,
                        TransactionReadRepository.UNCATEGORIZED,
                        PageRequest.of(0, 10));

        assertThat(uncategorized.getTotalElements()).isEqualTo(1);
        assertThat(repository.findHistoryCategories(7L)).containsExactly("Food", "Income");
        assertThat(repository.historyHasUncategorized(7L)).isTrue();
        assertThat(repository.findHistoryCategories(8L)).containsExactly("Food");
        assertThat(repository.historyHasUncategorized(8L)).isFalse();
    }

    @Test
    void ignoresBlankTextAndCategoryFilters() {
        Page<com.finanzbuddy.api.document.dto.TransactionResponse> page =
                repository.findHistory(7L, "   ", null, null, "   ", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    private void insertTransaction(
            long id,
            long documentId,
            long userId,
            java.sql.Date bookingDate,
            String counterparty,
            String description,
            String category) {
        jdbcTemplate.update(
                "INSERT INTO transactions (id, document_id, user_id, booking_date, amount,"
                        + " currency, counterparty, description, category) VALUES (?, ?, ?, ?, ?,"
                        + " 'EUR', ?, ?, ?)",
                id,
                documentId,
                userId,
                bookingDate,
                new BigDecimal("12.50"),
                counterparty,
                description,
                category);
    }

    private static class DateValue {
        static java.sql.Date of(String value) {
            return java.sql.Date.valueOf(value);
        }
    }
}
