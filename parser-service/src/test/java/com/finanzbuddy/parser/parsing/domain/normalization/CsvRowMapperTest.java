package com.finanzbuddy.parser.parsing.domain.normalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finanzbuddy.parser.parsing.domain.model.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CsvRowMapperTest {

    private final CsvRowMapper mapper = new CsvRowMapper();

    @Test
    void mapsGermanColumnAliasesAndNormalizesValues() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("Buchungstag", "15.03.24");
        row.put("Valutadatum", "16.03.24");
        row.put("Betrag", "1.234,56");
        row.put("Waehrung", "EUR");
        row.put("Beguenstigter/Zahlungspflichtiger", "Supermarket");
        row.put("Verwendungszweck", "Groceries");
        row.put("Kategorie", "Food");

        Transaction tx = mapper.map(row, "{\"raw\":1}", 1L, 2L, Locale.GERMANY);

        assertThat(tx.documentId()).isEqualTo(1L);
        assertThat(tx.userId()).isEqualTo(2L);
        assertThat(tx.bookingDate()).isEqualTo(LocalDate.of(2024, 3, 15));
        assertThat(tx.valueDate()).isEqualTo(LocalDate.of(2024, 3, 16));
        assertThat(tx.amount()).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(tx.currency()).isEqualTo("EUR");
        assertThat(tx.counterparty()).isEqualTo("Supermarket");
        assertThat(tx.description()).isEqualTo("Groceries");
        assertThat(tx.category()).isEqualTo("Food");
        assertThat(tx.rawData()).isEqualTo("{\"raw\":1}");
    }

    @Test
    void dropsOverlongCurrencyToNull() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("BookingDate", "15.03.24");
        row.put("Amount", "-12.50");
        row.put("Currency", "Euro");

        Transaction tx = mapper.map(row, "{}", 1L, 2L, Locale.GERMANY);

        assertThat(tx.currency()).isNull();
    }

    @Test
    void leavesMissingOptionalColumnsNull() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("BookingDate", "2024-03-15");
        row.put("Amount", "10,00");

        Transaction tx = mapper.map(row, "{}", 1L, 2L, Locale.GERMANY);

        assertThat(tx.valueDate()).isNull();
        assertThat(tx.currency()).isNull();
        assertThat(tx.counterparty()).isNull();
        assertThat(tx.category()).isNull();
        assertThat(tx.bookingDate()).isEqualTo(LocalDate.of(2024, 3, 15));
        assertThat(tx.amount()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void rejectsInvalidAmount() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("BookingDate", "15.03.24");
        row.put("Amount", "not-a-number");

        assertThatThrownBy(() -> mapper.map(row, "{}", 1L, 2L, Locale.GERMANY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid amount value");
    }

    @Test
    void validateHeaderPassesWhenRequiredColumnsPresent() {
        mapper.validateHeader(new String[] {"BookingDate", "Amount", "Currency"});
    }

    @Test
    void validateHeaderFailsWhenRequiredColumnMissing() {
        assertThatThrownBy(() -> mapper.validateHeader(new String[] {"Name", "Value"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required column: Buchungstag");
    }
}
