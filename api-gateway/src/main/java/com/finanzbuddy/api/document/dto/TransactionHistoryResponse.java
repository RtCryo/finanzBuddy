package com.finanzbuddy.api.document.dto;

import java.util.List;

public record TransactionHistoryResponse(
        List<TransactionResponse> content,
        long totalElements,
        int totalPages,
        int number,
        int size,
        List<String> categories,
        boolean hasUncategorized) {}
