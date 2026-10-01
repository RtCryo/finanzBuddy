package com.finanzbuddy.parser.parsing.adapter.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.parsing.domain.model.Transaction;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CsvParserAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CsvParserAdapter parser =
            new CsvParserAdapter(objectMapper, "ISO-8859-1", ";", 10000);

    @Test
    void parsesGermanDateFormat() {
        String csv =
                "BookingDate;ValueDate;Amount;Currency;Counterparty;Description;Category\n"
                        + "15.03.24;15.03.24;-12.50;EUR;Supermarket;Weekly groceries;Food";
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");

        List<Transaction> transactions = parser.parse(toStream(csv), event, Locale.GERMANY);

        assertThat(transactions).hasSize(1);
        Transaction tx = transactions.get(0);
        assertThat(tx.bookingDate()).isEqualTo(LocalDate.of(2024, 3, 15));
        assertThat(tx.valueDate()).isEqualTo(LocalDate.of(2024, 3, 15));
        assertThat(tx.amount()).isEqualByComparingTo(new BigDecimal("-12.50"));
        assertThat(tx.currency()).isEqualTo("EUR");
        assertThat(tx.counterparty()).isEqualTo("Supermarket");
        assertThat(tx.description()).isEqualTo("Weekly groceries");
        assertThat(tx.category()).isEqualTo("Food");
        assertThat(tx.documentId()).isEqualTo(1L);
        assertThat(tx.userId()).isEqualTo(2L);
        assertThat(tx.rawData()).isNotBlank();
    }

    @Test
    void supportsOnlyCsvType() {
        assertThat(parser.supports("CSV")).isTrue();
        assertThat(parser.supports("csv")).isTrue();
        assertThat(parser.supports("PDF")).isFalse();
    }

    @Test
    void rejectsMalformedCsvGracefully() {
        String csv =
                "BookingDate;ValueDate;Amount;Currency;Counterparty;Description;Category\n"
                        + "invalid-date;invalid-date;not-a-number;EUR;Counterparty;Description;Category";
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");

        assertThatThrownBy(() -> parser.parse(toStream(csv), event, Locale.GERMANY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCsvWithoutRequiredTransactionColumns() {
        String csv = "Name;Value\nAlice;100\n";
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");

        assertThatThrownBy(() -> parser.parse(toStream(csv), event, Locale.GERMANY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse CSV document")
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required column: Buchungstag");
    }

    @Test
    void fallsBackToGermanDateWhenLocaleMismatch() {
        String csv =
                "BookingDate;ValueDate;Amount;Currency;Counterparty;Description;Category\n"
                        + "01.12.23;01.12.23;100.00;EUR;Employer;Salary;Income";
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");

        List<Transaction> transactions = parser.parse(toStream(csv), event, Locale.US);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).bookingDate()).isEqualTo(LocalDate.of(2023, 12, 1));
    }

    @Test
    void parsesRealGermanBankStatementCsv() throws IOException {
        InputStream realCsv = getClass().getResourceAsStream("/20260924-76085158-umsatz.CSV");
        assertThat(realCsv).isNotNull();
        DocumentUploadedEvent event = new DocumentUploadedEvent(99L, 88L, "CSV", "csv/99");

        try (InputStream inputStream = realCsv) {
            List<Transaction> transactions = parser.parse(inputStream, event, Locale.GERMANY);

            assertThat(transactions).hasSize(307);
            assertThat(transactions)
                    .anyMatch(tx -> tx.amount().compareTo(BigDecimal.ZERO) > 0)
                    .anyMatch(tx -> tx.amount().compareTo(BigDecimal.ZERO) < 0);
            Transaction first = transactions.get(0);
            assertThat(first.bookingDate()).isEqualTo(LocalDate.of(2026, 9, 23));
            assertThat(first.valueDate()).isEqualTo(LocalDate.of(2026, 9, 23));
            assertThat(first.amount()).isEqualByComparingTo(new BigDecimal("-22.09"));
            assertThat(first.currency()).isEqualTo("EUR");
            assertThat(first.counterparty()).isEqualTo("AMAZON PAYMENTS EUROPE S.C.A.");
            assertThat(first.category()).isEqualTo("Einkäufe");
            assertThat(first.rawData()).isNotBlank();
        }
    }

    @Test
    void parsesGermanAmountWithThousandSeparator() {
        String csv =
                "BookingDate;ValueDate;Amount;Currency;Counterparty;Description;Category\n"
                        + "15.03.24;15.03.24;1.234,56;EUR;Supermarket;Weekly groceries;Food";
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");

        List<Transaction> transactions = parser.parse(toStream(csv), event, Locale.GERMANY);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).amount()).isEqualByComparingTo(new BigDecimal("1234.56"));
    }

    @Test
    void preservesSmallGermanDecimalWithThreeFractionalDigits() {
        String csv =
                "BookingDate;ValueDate;Amount;Currency;Counterparty;Description;Category\n"
                        + "15.03.24;15.03.24;0,123;EUR;Test;Test;Other";
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");

        List<Transaction> transactions = parser.parse(toStream(csv), event, Locale.GERMANY);

        assertThat(transactions.get(0).amount()).isEqualByComparingTo(new BigDecimal("0.123"));
    }

    @Test
    void interpretsAmbiguousThousandsSeparatorsUsingConfiguredLocale() {
        String csv =
                "BookingDate;ValueDate;Amount;Currency;Counterparty;Description;Category\n"
                        + "15.03.24;15.03.24;1,234;EUR;Test;Test;Other";
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        CsvParserAdapter englishParser = new CsvParserAdapter(objectMapper, "UTF-8", ";", 10000);

        List<Transaction> german = parser.parse(toStream(csv), event, Locale.GERMANY);
        List<Transaction> english = englishParser.parse(toStream(csv), event, Locale.US);

        assertThat(german.get(0).amount()).isEqualByComparingTo(new BigDecimal("1.234"));
        assertThat(english.get(0).amount()).isEqualByComparingTo(new BigDecimal("1234"));
    }

    @Test
    void parsesAmountsWithNonBreakingSpaceGrouping() {
        String csv =
                "BookingDate;ValueDate;Amount;Currency;Counterparty;Description;Category\n"
                        + "15.03.24;15.03.24;1 234,56;EUR;Test;Test;Other";
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");
        CsvParserAdapter utf8Parser = new CsvParserAdapter(objectMapper, "UTF-8", ";", 10000);

        List<Transaction> transactions =
                utf8Parser.parse(
                        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                        event,
                        Locale.GERMANY);

        assertThat(transactions.get(0).amount()).isEqualByComparingTo(new BigDecimal("1234.56"));
    }

    @Test
    void stripsBomFromUtf8Csv() {
        String csv =
                "BookingDate;ValueDate;Amount;Currency;Counterparty;Description;Category\n"
                        + "15.03.24;15.03.24;-12.50;EUR;Supermarket;Weekly groceries;Food";
        byte[] utf8Bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] csvBytes = csv.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[utf8Bom.length + csvBytes.length];
        System.arraycopy(utf8Bom, 0, withBom, 0, utf8Bom.length);
        System.arraycopy(csvBytes, 0, withBom, utf8Bom.length, csvBytes.length);
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");

        List<Transaction> transactions =
                parser.parse(new ByteArrayInputStream(withBom), event, Locale.GERMANY);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).amount()).isEqualByComparingTo(new BigDecimal("-12.50"));
    }

    @Test
    void supportsCommaSeparator() {
        CsvParserAdapter commaParser = new CsvParserAdapter(objectMapper, "UTF-8", ",", 10000);
        String csv =
                "BookingDate,ValueDate,Amount,Currency,Counterparty,Description,Category\n"
                        + "15.03.24,15.03.24,-12.50,EUR,Supermarket,Weekly groceries,Food";
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");

        List<Transaction> transactions = commaParser.parse(toStream(csv), event, Locale.GERMANY);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).amount()).isEqualByComparingTo(new BigDecimal("-12.50"));
    }

    @Test
    void rejectsCsvExceedingMaxRows() {
        StringBuilder csv =
                new StringBuilder(
                        "BookingDate;ValueDate;Amount;Currency;Counterparty;Description;Category\n");
        for (int i = 0; i < 5; i++) {
            csv.append("15.03.24;15.03.24;-12.50;EUR;Supermarket;Weekly groceries;Food\n");
        }
        CsvParserAdapter limitedParser = new CsvParserAdapter(objectMapper, "UTF-8", ";", 2);
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");

        assertThatThrownBy(
                        () -> limitedParser.parse(toStream(csv.toString()), event, Locale.GERMANY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse CSV document")
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum allowed rows");
    }

    @Test
    void normalizesOverlongCurrencyToNullInsteadOfFailing() {
        // A stray label like "Euro" is longer than the 3-char currency column; it must be dropped
        // (kept in raw_data) rather than blow up the insert and FAIL the whole document.
        String csv =
                "BookingDate;ValueDate;Amount;Currency;Counterparty;Description;Category\n"
                        + "15.03.24;15.03.24;-12.50;Euro;Supermarket;Weekly groceries;Food";
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");

        List<Transaction> transactions = parser.parse(toStream(csv), event, Locale.GERMANY);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).currency()).isNull();
        assertThat(transactions.get(0).rawData()).contains("Euro");
    }

    @Test
    void parsesIsoDateFormat() {
        String csv =
                "BookingDate;ValueDate;Amount;Currency;Counterparty;Description;Category\n"
                        + "2024-03-15;2024-03-15;-12.50;EUR;Supermarket;Weekly groceries;Food";
        DocumentUploadedEvent event = new DocumentUploadedEvent(1L, 2L, "CSV", "csv/1");

        List<Transaction> transactions = parser.parse(toStream(csv), event, Locale.GERMANY);

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).bookingDate()).isEqualTo(LocalDate.of(2024, 3, 15));
    }

    private InputStream toStream(String value) {
        return new ByteArrayInputStream(value.getBytes(Charset.forName("ISO-8859-1")));
    }
}
