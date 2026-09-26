package com.finanzbuddy.parser.parser.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finanzbuddy.parser.entity.Transaction;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CsvTransactionParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CsvTransactionParser parser = new CsvTransactionParser(objectMapper);

    @Test
    void parsesGermanDateFormat() {
        String csv = "BookingDate;ValueDate;Amount;Currency;Counterparty;Description;Category\n"
                + "15.03.24;15.03.24;-12.50;EUR;Supermarket;Weekly groceries;Food";
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");

        List<Transaction> transactions = parser.parse(toStream(csv), event, Locale.GERMANY);

        assertThat(transactions).hasSize(1);
        Transaction tx = transactions.get(0);
        assertThat(tx.getBookingDate()).isEqualTo(LocalDate.of(2024, 3, 15));
        assertThat(tx.getValueDate()).isEqualTo(LocalDate.of(2024, 3, 15));
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("-12.50"));
        assertThat(tx.getCurrency()).isEqualTo("EUR");
        assertThat(tx.getCounterparty()).isEqualTo("Supermarket");
        assertThat(tx.getDescription()).isEqualTo("Weekly groceries");
        assertThat(tx.getCategory()).isEqualTo("Food");
        assertThat(tx.getDocumentId()).isEqualTo(1L);
        assertThat(tx.getUserId()).isEqualTo(2L);
        assertThat(tx.getRawData()).isNotBlank();
    }

    @Test
    void supportsOnlyCsvType() {
        assertThat(parser.supports("CSV")).isTrue();
        assertThat(parser.supports("csv")).isTrue();
        assertThat(parser.supports("PDF")).isFalse();
    }

    @Test
    void rejectsMalformedCsvGracefully() {
        String csv = "BookingDate;ValueDate;Amount;Currency;Counterparty;Description;Category\n"
                + "invalid-date;invalid-date;not-a-number;EUR;Counterparty;Description;Category";
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");

        assertThatThrownBy(() -> parser.parse(toStream(csv), event, Locale.GERMANY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fallsBackToGermanDateWhenLocaleMismatch() {
        String csv = "BookingDate;ValueDate;Amount;Currency;Counterparty;Description;Category\n"
                + "01.12.23;01.12.23;100.00;EUR;Employer;Salary;Income";
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");

        List<Transaction> transactions = parser.parse(toStream(csv), event, Locale.US);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getBookingDate()).isEqualTo(LocalDate.of(2023, 12, 1));
    }

    private InputStream toStream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
