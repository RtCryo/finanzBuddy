package com.finanzbuddy.api.document.adapter.in.web.dto;

import java.util.List;

public record DocumentListResponse(
        List<DocumentResponse> content,
        long totalElements,
        int totalPages,
        int number,
        int size,
        long totalDocuments,
        long processedCount,
        long inProgressCount) {}
