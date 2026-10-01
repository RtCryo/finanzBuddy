package com.finanzbuddy.api.document.adapter.in.web.dto;

import com.finanzbuddy.api.document.domain.model.DocumentStatus;
import jakarta.validation.constraints.NotNull;
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
 */
public record DocumentStatusUpdateRequest(
        @NotNull DocumentStatus status, @Size(max = 4000) String message) {}
