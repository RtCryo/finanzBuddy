package com.finanzbuddy.api.document.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RelatedExpenseResponse(
        Long id, LocalDate valueDate, BigDecimal amount, String currency) {}
