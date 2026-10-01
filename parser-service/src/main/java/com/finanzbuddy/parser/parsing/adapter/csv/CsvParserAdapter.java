package com.finanzbuddy.parser.parsing.adapter.csv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.parsing.domain.model.Transaction;
import com.finanzbuddy.parser.parsing.domain.normalization.CsvRowMapper;
import com.finanzbuddy.parser.parsing.port.DocumentParser;
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
public class CsvParserAdapter implements DocumentParser {

    private static final Logger logger = LoggerFactory.getLogger(CsvParserAdapter.class);

    private final ObjectMapper objectMapper;
    private final Charset csvCharset;
    private final char separator;
    private final int maxRows;
    private final CsvRowMapper rowMapper = new CsvRowMapper();

    public CsvParserAdapter(
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
            rowMapper.validateHeader(header);

            List<Transaction> transactions = new ArrayList<>();
            String[] line;
            int rowCount = 0;
            while ((line = csvReader.readNext()) != null) {
                rowCount++;
                if (rowCount > maxRows) {
                    throw new IllegalArgumentException(
                            "CSV document exceeds maximum allowed rows: " + maxRows);
                }
                Map<String, String> row = buildRow(header, line);
                transactions.add(
                        rowMapper.map(
                                row,
                                serializeRawData(row),
                                event.documentId(),
                                event.userId(),
                                locale));
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
        if (first.charAt(0) == '﻿') {
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

    private String serializeRawData(Map<String, String> row) {
        try {
            return objectMapper.writeValueAsString(row);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to serialize raw CSV row", e);
            return "{}";
        }
    }
}
