package com.finanzbuddy.api.document.dto;

import com.finanzbuddy.api.entity.DocumentStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating a document's processing status from an internal parser service.
 *
 * <p>The {@code message} field is intentionally capped at 4000 characters. It maps to the {@code
 * error_message} database column (TEXT), and without a length limit a parser could push an
 * arbitrarily large string into the database. That inflates storage and backups, increases memory
 * usage when the message is serialized back to JSON responses, and can be abused as a DoS vector by
 * repeatedly reporting failures with multi-megabyte payloads. A 4000-character cap is enough for
 * any reasonable parser error while keeping the table and API responses bounded.
 *
 * <p>{@code savedTransactions} and {@code skippedDuplicates} are optional, non-negative, and only
 * meaningful with {@code PROCESSED}; they are given together or not at all. A parser that predates
 * deduplication sends neither.
 */
public record DocumentStatusUpdateRequest(
        @NotNull DocumentStatus status,
        @Size(max = 4000) String message,
        @PositiveOrZero Integer savedTransactions,
        @PositiveOrZero Integer skippedDuplicates) {

    public DocumentStatusUpdateRequest(DocumentStatus status, String message) {
        this(status, message, null, null);
    }

    @JsonIgnore
    @AssertTrue(message = "savedTransactions and skippedDuplicates must be given together")
    public boolean isDeduplicationCountsComplete() {
        return (savedTransactions == null) == (skippedDuplicates == null);
    }

    public boolean hasDeduplicationCounts() {
        return savedTransactions != null && skippedDuplicates != null;
    }
}
