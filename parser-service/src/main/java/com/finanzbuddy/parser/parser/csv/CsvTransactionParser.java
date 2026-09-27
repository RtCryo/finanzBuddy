package com.finanzbuddy.parser.parser.csv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finanzbuddy.parser.entity.Transaction;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.parser.DocumentParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.io.input.BOMInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CsvTransactionParser implements DocumentParser {

    private static final Logger logger = LoggerFactory.getLogger(CsvTransactionParser.class);

    private final ObjectMapper objectMapper;
    private final Charset csvCharset;
    private final char separator;
    private final int maxRows;

    public CsvTransactionParser(
            ObjectMapper objectMapper,
            @Value("${parser.formats.csv-charset:windows-1252}") String csvCharset,
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
            validateHeader(header);

            List<Transaction> transactions = new ArrayList<>();
            String[] line;
            int rowCount = 0;
            while ((line = csvReader.readNext()) != null) {
                rowCount++;
                if (rowCount > maxRows) {
                    throw new IllegalArgumentException(
                            "CSV document exceeds maximum allowed rows: " + maxRows);
                }
                transactions.add(mapToTransaction(buildRow(header, line), event, locale));
            }

            return transactions;
        } catch (IOException e) {
            logger.error(
                    "I/O failure while reading CSV document: documentId={}", event.documentId(), e);
            throw new UncheckedIOException("Failed to read CSV document " + event.documentId(), e);
        } catch (CsvValidationException | IllegalArgumentException e) {
            logger.error("Failed to parse CSV document: documentId={}", event.documentId(), e);
            throw new IllegalArgumentException(
                    "Failed to parse CSV document " + event.documentId(), e);
        } catch (Exception e) {
            logger.error("Unexpected CSV parser failure: documentId={}", event.documentId(), e);
            throw new IllegalStateException(
                    "Unexpected failure parsing CSV document " + event.documentId(), e);
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

    private void validateHeader(String[] header) {
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

    private Map<String, String> buildRow(String[] header, String[] values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < header.length && i < values.length; i++) {
            row.put(header[i], values[i]);
        }
        return row;
    }

    private Transaction mapToTransaction(
            Map<String, String> row, DocumentUploadedEvent event, Locale locale) {
        Transaction transaction = new Transaction();
        transaction.setDocumentId(event.documentId());
        transaction.setUserId(event.userId());
        transaction.setBookingDate(
                CsvDateParser.parse(
                        getColumnValue(row, CsvColumn.BOOKING_DATE), locale, "bookingDate"));
        transaction.setValueDate(
                CsvDateParser.parse(
                        getColumnValue(row, CsvColumn.VALUE_DATE), locale, "valueDate"));
        transaction.setAmount(CsvAmountParser.parse(getColumnValue(row, CsvColumn.AMOUNT), locale));
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
