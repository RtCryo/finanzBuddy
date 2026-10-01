package com.finanzbuddy.api.document.domain.model;

public record DocumentListResult(
        PageResult<Document> page,
        long totalDocuments,
        long processedCount,
        long inProgressCount) {}
