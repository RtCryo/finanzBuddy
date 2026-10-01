package com.finanzbuddy.parser.parsing.domain.normalization;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class CsvDateParser {

    private static final DateTimeFormatter GERMAN_SHORT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yy");
    private static final DateTimeFormatter GERMAN_LONG_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter ISO_LIKE_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ConcurrentMap<Locale, DateTimeFormatter> LOCALE_FORMATTERS =
            new ConcurrentHashMap<>();
    private static final List<DateTimeFormatter> FALLBACK_FORMATTERS =
            List.of(
                    GERMAN_SHORT_DATE_FORMATTER,
                    GERMAN_LONG_DATE_FORMATTER,
                    ISO_LIKE_DATE_FORMATTER);

    private CsvDateParser() {}

    static LocalDate parse(String value, Locale locale, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Locale resolvedLocale = locale == null ? Locale.GERMANY : locale;
        DateTimeFormatter localeFormatter =
                LOCALE_FORMATTERS.computeIfAbsent(
                        resolvedLocale,
                        key ->
                                new DateTimeFormatterBuilder()
                                        .appendLocalized(FormatStyle.SHORT, null)
                                        .toFormatter(key)
                                        .withResolverStyle(ResolverStyle.SMART));
        String trimmed = value.trim();
        try {
            return LocalDate.parse(trimmed, localeFormatter);
        } catch (DateTimeParseException ignored) {
            // Try supported bank-export formats.
        }
        for (DateTimeFormatter formatter : FALLBACK_FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported format.
            }
        }
        throw new IllegalArgumentException("Invalid date value for " + fieldName + ": " + value);
    }
}
