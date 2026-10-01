package com.finanzbuddy.api.document.adapter.in.web;

import com.finanzbuddy.api.document.adapter.in.web.dto.DocumentListResponse;
import com.finanzbuddy.api.document.adapter.in.web.dto.DocumentResponse;
import com.finanzbuddy.api.document.adapter.in.web.dto.TransactionResponse;
import com.finanzbuddy.api.document.domain.model.Document;
import com.finanzbuddy.api.document.domain.model.DocumentListResult;
import com.finanzbuddy.api.document.domain.model.DocumentStatus;
import com.finanzbuddy.api.document.domain.model.Transaction;
import java.util.List;

final class DocumentWebMapper {

    private DocumentWebMapper() {}

    static DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.id(),
                document.originalFilename(),
                document.type(),
                document.status(),
                document.createdAt(),
                document.status() == DocumentStatus.FAILED ? document.errorMessage() : null);
    }

    static DocumentListResponse toListResponse(DocumentListResult result) {
        return new DocumentListResponse(
                result.page().items().stream().map(DocumentWebMapper::toResponse).toList(),
                result.page().totalElements(),
                result.page().totalPages(),
                result.page().page(),
                result.page().size(),
                result.totalDocuments(),
                result.processedCount(),
                result.inProgressCount());
    }

    static List<TransactionResponse> toTransactionResponses(List<Transaction> transactions) {
        return transactions.stream().map(DocumentWebMapper::toTransactionResponse).toList();
    }

    private static TransactionResponse toTransactionResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.id(),
                transaction.bookingDate(),
                transaction.valueDate(),
                transaction.amount(),
                transaction.currency(),
                transaction.counterparty(),
                transaction.description(),
                transaction.category());
    }
}
