package com.finanzbuddy.parser.parser.csv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finanzbuddy.parser.entity.Transaction;
import com.finanzbuddy.parser.event.DocumentUploadedEvent;
import com.finanzbuddy.parser.parser.DocumentParser;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CsvTransactionParser implements DocumentParser {

    private static final Logger logger = LoggerFactory.getLogger(CsvTransactionParser.class);

    private final ObjectMapper objectMapper;

    public CsvTransactionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private static final DateTimeFormatter GERMAN_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy");

    @Override
    public boolean supports(String documentType) {
        return "CSV".equalsIgnoreCase(documentType);
    }

    @Override
    public List<Transaction> parse(InputStream inputStream, DocumentUploadedEvent event, Locale locale) {
        logger.info("Parsing CSV document: documentId={}", event.documentId());
        DateTimeFormatter dateFormatter = resolveDateFormatter(locale);

        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            CsvToBean<CsvTransactionRow> csvToBean = new CsvToBeanBuilder<CsvTransactionRow>(reader)
                    .withType(CsvTransactionRow.class)
                    .withSeparator(';')
                    .withIgnoreLeadingWhiteSpace(true)
                    .build();

            List<CsvTransactionRow> rows = csvToBean.parse();
            return rows.stream()
                    .map(row -> mapToTransaction(row, event, dateFormatter))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to parse CSV document: documentId={}", event.documentId(), e);
            throw new IllegalArgumentException("Failed to parse CSV document " + event.documentId(), e);
        }
    }

    private Transaction mapToTransaction(CsvTransactionRow row, DocumentUploadedEvent event, DateTimeFormatter dateFormatter) {
        Transaction transaction = new Transaction();
        transaction.setDocumentId(event.documentId());
        transaction.setUserId(event.userId());
        transaction.setBookingDate(parseDate(row.getBookingDate(), dateFormatter, "bookingDate"));
        transaction.setValueDate(parseDate(row.getValueDate(), dateFormatter, "valueDate"));
        transaction.setAmount(row.getAmount());
        transaction.setCurrency(row.getCurrency());
        transaction.setCounterparty(row.getCounterparty());
        transaction.setDescription(row.getDescription());
        transaction.setCategory(row.getCategory());
        transaction.setRawData(serializeRawData(row));
        return transaction;
    }

    private String serializeRawData(CsvTransactionRow row) {
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
            logger.warn("Failed to parse {} value '{}' with configured locale, trying German fallback", fieldName, value);
            try {
                return LocalDate.parse(value.trim(), GERMAN_DATE_FORMATTER);
            } catch (DateTimeParseException fallback) {
                throw new IllegalArgumentException("Invalid date value for " + fieldName + ": " + value, fallback);
            }
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
}
