package com.finanzbuddy.parser.repository;

import com.finanzbuddy.parser.entity.Transaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Identity of a transaction for one owner: booking date, value date, amount, currency,
 * counterparty and description. Strings are trimmed and a missing string equals an empty one; the
 * amount is normalized to scale 4 so that equal values compare equal.
 */
public record StoredTransactionKey(
        LocalDate bookingDate,
        LocalDate valueDate,
        BigDecimal amount,
        String currency,
        String counterparty,
        String description) {

    private static final int AMOUNT_SCALE = 4;

    public StoredTransactionKey {
        amount = amount == null ? null : amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        currency = normalize(currency);
        counterparty = normalize(counterparty);
        description = normalize(description);
    }

    public static StoredTransactionKey of(Transaction transaction) {
        return new StoredTransactionKey(
                transaction.getBookingDate(),
                transaction.getValueDate(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getCounterparty(),
                transaction.getDescription());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
