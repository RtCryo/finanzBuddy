package com.finanzbuddy.parser.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TransactionTest {

    @Test
    void prePersistSetsCreatedAt() {
        Transaction transaction = new Transaction();
        assertThat(transaction.getCreatedAt()).isNull();

        transaction.onCreate();

        assertThat(transaction.getCreatedAt()).isNotNull();
    }

    @Test
    void gettersAndSettersWork() {
        Transaction transaction = new Transaction();
        transaction.setId(1L);
        transaction.setDocumentId(2L);
        transaction.setUserId(3L);
        transaction.setBookingDate(LocalDate.of(2024, 1, 1));
        transaction.setValueDate(LocalDate.of(2024, 1, 2));
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency("EUR");
        transaction.setCounterparty("Employer");
        transaction.setDescription("Salary");
        transaction.setCategory("Income");
        transaction.setRawData("{\"key\":\"value\"}");
        transaction.setCreatedAt(Instant.now());

        assertThat(transaction.getId()).isEqualTo(1L);
        assertThat(transaction.getDocumentId()).isEqualTo(2L);
        assertThat(transaction.getUserId()).isEqualTo(3L);
        assertThat(transaction.getBookingDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(transaction.getValueDate()).isEqualTo(LocalDate.of(2024, 1, 2));
        assertThat(transaction.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(transaction.getCurrency()).isEqualTo("EUR");
        assertThat(transaction.getCounterparty()).isEqualTo("Employer");
        assertThat(transaction.getDescription()).isEqualTo("Salary");
        assertThat(transaction.getCategory()).isEqualTo("Income");
        assertThat(transaction.getRawData()).isEqualTo("{\"key\":\"value\"}");
        assertThat(transaction.getCreatedAt()).isNotNull();
    }
}
