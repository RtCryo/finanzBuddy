package com.finanzbuddy.parser.parsing.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Framework-free transaction extracted from a document, shared across parser-service slices. */
public record Transaction(
        Long documentId,
        Long userId,
        LocalDate bookingDate,
        LocalDate valueDate,
        BigDecimal amount,
        String currency,
        String counterparty,
        String description,
        String category,
        String rawData) {}
