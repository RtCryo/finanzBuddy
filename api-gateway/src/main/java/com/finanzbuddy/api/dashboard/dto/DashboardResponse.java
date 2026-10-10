package com.finanzbuddy.api.dashboard.dto;

public record DashboardResponse(
        long totalDocuments,
        long processedDocuments,
        long inProgressDocuments,
        long savedTransactions,
        long uniqueCategories,
        long uniqueCounterparties) {}
