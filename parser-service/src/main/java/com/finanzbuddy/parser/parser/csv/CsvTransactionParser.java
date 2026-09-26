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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CsvTransactionParser implements DocumentParser {

    private static final Logger logger = LoggerFactory.getLogger(CsvTransactionParser.class);

    private static final DateTimeFormatter GERMAN_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yy");

    private final ObjectMapper objectMapper;
    private final Charset csvCharset;

    public CsvTransactionParser(
            ObjectMapper objectMapper,
            @Value("${parser.formats.csv-charset:ISO-8859-1}") String csvCharset) {
        this.objectMapper = objectMapper;
        this.csvCharset = Charset.forName(csvCharset);
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

        try (Reader reader = new InputStreamReader(inputStream, csvCharset);
                CSVReader csvReader =
                        new CSVReaderBuilder(reader)
                                .withCSVParser(
                                        new CSVParserBuilder()
                                                .withSeparator(';')
                                                .withQuoteChar('"')
                                                .build())
                                .build()) {

            String[] header = csvReader.readNext();
            if (header == null) {
                logger.warn("CSV document is empty: documentId={}", event.documentId());
                return List.of();
            }

            List<Map<String, String>> rows = new ArrayList<>();
            String[] line;
            while ((line = csvReader.readNext()) != null) {
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
            if (row.containsKey(name)) {
                return row.get(name);
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
        try {
            return LocalDate.parse(value.trim(), dateFormatter);
        } catch (DateTimeParseException e) {
            logger.warn(
                    "Failed to parse {} value '{}' with configured locale, trying German fallback",
                    fieldName,
                    value);
            try {
                return LocalDate.parse(value.trim(), GERMAN_DATE_FORMATTER);
            } catch (DateTimeParseException fallback) {
                throw new IllegalArgumentException(
                        "Invalid date value for " + fieldName + ": " + value, fallback);
            }
        }
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace(" ", "").replace(',', '.');
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount value: " + value, e);
        }
    }

    private DateTimeFormatter resolveDateFormatter(Locale locale) {
        if (locale == null) {
            locale = Locale.GERMANY;
        }
        return new DateTimeFormatterBuilder()
                .appendLocalized(FormatStyle.SHORT, null)
                .toFormatter(locale);
    }

    private enum CsvColumn {
        BOOKING_DATE("Buchungstag", "BookingDate"),
        VALUE_DATE("Valutadatum", "ValueDate"),
        AMOUNT("Betrag", "Amount"),
        CURRENCY("Waehrung", "Currency"),
        COUNTERPARTY("Beguenstigter/Zahlungspflichtiger", "Counterparty"),
        DESCRIPTION("Verwendungszweck", "Description"),
        CATEGORY("Kategorie", "Category");

        private final List<String> names;

        CsvColumn(String... names) {
            this.names = Arrays.asList(names);
        }
    }
}
