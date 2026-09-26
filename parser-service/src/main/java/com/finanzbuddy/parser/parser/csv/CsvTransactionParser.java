package com.finanzbuddy.parser.parser.csv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finanzbuddy.parser.entity.Transaction;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.parser.DocumentParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.io.input.BOMInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CsvTransactionParser implements DocumentParser {

    private static final Logger logger = LoggerFactory.getLogger(CsvTransactionParser.class);

    private static final DateTimeFormatter GERMAN_SHORT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yy");
    private static final DateTimeFormatter GERMAN_LONG_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter ISO_LIKE_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ObjectMapper objectMapper;
    private final Charset csvCharset;
    private final char separator;
    private final int maxRows;

    public CsvTransactionParser(
            ObjectMapper objectMapper,
            @Value("${parser.formats.csv-charset:ISO-8859-1}") String csvCharset,
            @Value("${parser.formats.csv-separator:;}") String separator,
            @Value("${parser.formats.csv-max-rows:100000}") int maxRows) {
        this.objectMapper = objectMapper;
        this.csvCharset = Charset.forName(csvCharset);
        this.separator = parseSeparator(separator);
        this.maxRows = maxRows;
    }

    @Override
    public boolean supports(String documentType) {
        return "CSV".equalsIgnoreCase(documentType);
    }

    @Override
    public List<Transaction> parse(
            InputStream inputStream, DocumentUploadedEvent event, Locale locale) {
        logger.info("Parsing CSV document: documentId={}", event.documentId());
        DateTimeFormatter dateFormatter = resolveDateFormatter(locale);

        try (BOMInputStream bomInputStream =
                        BOMInputStream.builder().setInputStream(inputStream).get();
                Reader reader = new InputStreamReader(bomInputStream, csvCharset);
                CSVReader csvReader =
                        new CSVReaderBuilder(reader)
                                .withCSVParser(
                                        new CSVParserBuilder()
                                                .withSeparator(separator)
                                                .withQuoteChar('"')
                                                .build())
                                .build()) {

            String[] header = csvReader.readNext();
            if (header == null) {
                logger.warn("CSV document is empty: documentId={}", event.documentId());
                return List.of();
            }
            header = stripBomFromFirstHeader(header);

            List<Map<String, String>> rows = new ArrayList<>();
            String[] line;
            int rowCount = 0;
            while ((line = csvReader.readNext()) != null) {
                rowCount++;
                if (rowCount > maxRows) {
                    throw new IllegalArgumentException(
                            "CSV document exceeds maximum allowed rows: " + maxRows);
                }
                rows.add(buildRow(header, line));
            }

            return rows.stream()
                    .map(row -> mapToTransaction(row, event, dateFormatter))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to parse CSV document: documentId={}", event.documentId(), e);
            throw new IllegalArgumentException(
                    "Failed to parse CSV document " + event.documentId(), e);
        }
    }

    private char parseSeparator(String separator) {
        if (separator == null || separator.isBlank()) {
            return ';';
        }
        if (separator.length() == 1) {
            return separator.charAt(0);
        }
        return switch (separator.toLowerCase(Locale.ROOT)) {
            case "tab", "\\t" -> '\t';
            case "comma" -> ',';
            case "semicolon" -> ';';
            default -> throw new IllegalArgumentException("Invalid CSV separator: " + separator);
        };
    }

    private String[] stripBomFromFirstHeader(String[] header) {
        if (header.length == 0 || header[0].isEmpty()) {
            return header;
        }
        // BOMInputStream removes the BOM bytes, but if the file was read as UTF-8
        // without BOM awareness, the first header may still contain the zero-width
        // BOM character (U+FEFF). Strip it defensively.
        String first = header[0];
        if (first.charAt(0) == '\uFEFF') {
            header[0] = first.substring(1);
        }
        return header;
    }

    private Map<String, String> buildRow(String[] header, String[] values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < header.length && i < values.length; i++) {
            row.put(header[i], values[i]);
        }
        return row;
    }

    private Transaction mapToTransaction(
            Map<String, String> row, DocumentUploadedEvent event, DateTimeFormatter dateFormatter) {
        Transaction transaction = new Transaction();
        transaction.setDocumentId(event.documentId());
        transaction.setUserId(event.userId());
        transaction.setBookingDate(
                parseDate(
                        getColumnValue(row, CsvColumn.BOOKING_DATE), dateFormatter, "bookingDate"));
        transaction.setValueDate(
                parseDate(getColumnValue(row, CsvColumn.VALUE_DATE), dateFormatter, "valueDate"));
        transaction.setAmount(parseAmount(getColumnValue(row, CsvColumn.AMOUNT)));
        transaction.setCurrency(getColumnValue(row, CsvColumn.CURRENCY));
        transaction.setCounterparty(getColumnValue(row, CsvColumn.COUNTERPARTY));
        transaction.setDescription(getColumnValue(row, CsvColumn.DESCRIPTION));
        transaction.setCategory(getColumnValue(row, CsvColumn.CATEGORY));
        transaction.setRawData(serializeRawData(row));
        return transaction;
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

    private String serializeRawData(Map<String, String> row) {
        try {
            return objectMapper.writeValueAsString(row);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize raw CSV row", e);
            return "{}";
        }
    }

    private LocalDate parseDate(String value, DateTimeFormatter dateFormatter, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return parseWithFormatter(trimmed, dateFormatter, fieldName)
                .orElseGet(
                        () ->
                                parseWithFormatter(trimmed, GERMAN_SHORT_DATE_FORMATTER, fieldName)
                                        .orElseGet(
                                                () ->
                                                        parseWithFormatter(
                                                                        trimmed,
                                                                        GERMAN_LONG_DATE_FORMATTER,
                                                                        fieldName)
                                                                .orElseGet(
                                                                        () ->
                                                                                parseWithFormatter(
                                                                                                trimmed,
                                                                                                ISO_LIKE_DATE_FORMATTER,
                                                                                                fieldName)
                                                                                        .orElseThrow(
                                                                                                () ->
                                                                                                        new IllegalArgumentException(
                                                                                                                "Invalid date value for "
                                                                                                                        + fieldName
                                                                                                                        + ": "
                                                                                                                        + value)))));
    }

    private java.util.Optional<LocalDate> parseWithFormatter(
            String value, DateTimeFormatter formatter, String fieldName) {
        try {
            return java.util.Optional.of(LocalDate.parse(value, formatter));
        } catch (DateTimeParseException e) {
            logger.debug(
                    "Failed to parse {} value '{}' with formatter {}",
                    fieldName,
                    value,
                    formatter.toString());
            return java.util.Optional.empty();
        }
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim().replace(" ", "");
        String normalized = normalizeAmount(trimmed);
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount value: " + value, e);
        }
    }

    private String normalizeAmount(String trimmed) {
        boolean hasComma = trimmed.contains(",");
        boolean hasDot = trimmed.contains(".");

        if (hasComma && hasDot) {
            // German format: 1.234,56 -> 1234.56
            return trimmed.replace(".", "").replace(',', '.');
        }

        if (hasComma && !hasDot) {
            int lastComma = trimmed.lastIndexOf(',');
            int fractionalDigits = trimmed.length() - lastComma - 1;

            if (trimmed.startsWith("0,")) {
                return trimmed.replace(',', '.');
            }

            // Heuristic: two fractional digits are common for currencies (German),
            // three or more digits after a single comma are usually an English thousands
            // separator, unless the value is tiny (e.g. 0,123).
            if (fractionalDigits == 2 && trimmed.length() > 3) {
                return trimmed.replace(',', '.');
            }
            if (fractionalDigits == 3 && lastComma == trimmed.length() - 4) {
                return trimmed.replace(",", "");
            }
            // Default to German decimal separator for ambiguous cases.
            return trimmed.replace(',', '.');
        }

        // Plain or English format: dot is the decimal separator.
        return trimmed;
    }

    private DateTimeFormatter resolveDateFormatter(Locale locale) {
        if (locale == null) {
            locale = Locale.GERMANY;
        }
        return new DateTimeFormatterBuilder()
                .appendLocalized(FormatStyle.SHORT, null)
                .toFormatter(locale)
                .withResolverStyle(ResolverStyle.SMART);
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
