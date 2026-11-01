package com.finanzbuddy.api.document.dto;

import java.util.List;

public record RelatedExpensePageResponse(
        List<RelatedExpenseResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
