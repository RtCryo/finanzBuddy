package com.finanzbuddy.parser.parsing.domain.normalization;

import com.finanzbuddy.parser.parsing.domain.model.Transaction;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps a raw CSV row (header-keyed values) to a domain {@link Transaction}, applying column
 * aliasing and locale-aware amount/date normalization. Framework-free: no CSV library, no Spring.
 */
public class CsvRowMapper {

    public void validateHeader(String[] header) {
        for (CsvColumn requiredColumn : List.of(CsvColumn.BOOKING_DATE, CsvColumn.AMOUNT)) {
            boolean found =
                    Arrays.stream(header)
                            .filter(name -> name != null)
                            .map(String::trim)
                            .anyMatch(
                                    name ->
                                            requiredColumn.names.stream()
                                                    .anyMatch(
                                                            alias -> alias.equalsIgnoreCase(name)));
            if (!found) {
                throw new IllegalArgumentException(
                        "CSV document is missing required column: " + requiredColumn.names.get(0));
            }
        }
    }

    public Transaction map(
            Map<String, String> row, String rawData, Long documentId, Long userId, Locale locale) {
        return new Transaction(
                documentId,
                userId,
                CsvDateParser.parse(
                        getColumnValue(row, CsvColumn.BOOKING_DATE), locale, "bookingDate"),
                CsvDateParser.parse(getColumnValue(row, CsvColumn.VALUE_DATE), locale, "valueDate"),
                CsvAmountParser.parse(getColumnValue(row, CsvColumn.AMOUNT), locale),
                normalizeCurrency(getColumnValue(row, CsvColumn.CURRENCY)),
                getColumnValue(row, CsvColumn.COUNTERPARTY),
                getColumnValue(row, CsvColumn.DESCRIPTION),
                getColumnValue(row, CsvColumn.CATEGORY),
                rawData);
    }

    private String normalizeCurrency(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        // The currency column is a 3-char code (VARCHAR(3)). Anything longer is not a valid ISO
        // 4217 code (e.g. a stray label like "Euro"); store null rather than let an oversized value
        // blow up the batch insert and FAIL the whole document. The original stays in raw_data.
        String upper = trimmed.toUpperCase(Locale.ROOT);
        return upper.length() > 3 ? null : upper;
    }

    private String getColumnValue(Map<String, String> row, CsvColumn column) {
        for (String name : column.names) {
            for (Map.Entry<String, String> entry : row.entrySet()) {
                if (entry.getKey().trim().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private enum CsvColumn {
        BOOKING_DATE("Buchungstag", "BookingDate"),
        VALUE_DATE("Valutadatum", "ValueDate"),
        AMOUNT("Betrag", "Amount"),
        CURRENCY("Waehrung", "Währung", "Currency"),
        COUNTERPARTY("Beguenstigter/Zahlungspflichtiger", "Counterparty"),
        DESCRIPTION("Verwendungszweck", "Description"),
        CATEGORY("Kategorie", "Category");

        private final List<String> names;

        CsvColumn(String... names) {
            this.names = Arrays.asList(names);
        }
    }
}
