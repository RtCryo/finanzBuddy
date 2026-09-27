package com.finanzbuddy.parser.parser.csv;

import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Pattern;

final class CsvAmountParser {

    private static final Pattern COMMA_GROUPING = Pattern.compile("[+-]?\\d{1,3}(,\\d{3})+");
    private static final Pattern DOT_GROUPING = Pattern.compile("[+-]?\\d{1,3}(\\.\\d{3})+");

    private CsvAmountParser() {}

    static BigDecimal parse(String value, Locale locale) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().replaceAll("[\\s\\u00a0\\u202f]", "");
        Locale resolvedLocale = locale == null ? Locale.GERMANY : locale;
        char decimalSeparator =
                DecimalFormatSymbols.getInstance(resolvedLocale).getDecimalSeparator();
        boolean hasComma = normalized.indexOf(',') >= 0;
        boolean hasDot = normalized.indexOf('.') >= 0;

        if (hasComma && hasDot) {
            int comma = normalized.lastIndexOf(',');
            int dot = normalized.lastIndexOf('.');
            char actualDecimal = comma > dot ? ',' : '.';
            char grouping = actualDecimal == ',' ? '.' : ',';
            normalized = normalized.replace(String.valueOf(grouping), "");
            normalized = normalized.replace(actualDecimal, '.');
        } else if (hasComma) {
            if (decimalSeparator == ',' || !COMMA_GROUPING.matcher(normalized).matches()) {
                normalized = normalized.replace(',', '.');
            } else {
                normalized = normalized.replace(",", "");
            }
        } else if (hasDot) {
            if (decimalSeparator == '.' || !DOT_GROUPING.matcher(normalized).matches()) {
                normalized = normalized.replace(',', '.');
            } else {
                normalized = normalized.replace(".", "");
            }
        }

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount value: " + value, e);
        }
    }
}
