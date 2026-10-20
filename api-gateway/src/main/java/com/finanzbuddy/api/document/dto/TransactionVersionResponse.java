package com.finanzbuddy.api.document.dto;

import java.time.Instant;

public record TransactionVersionResponse(
        int versionNumber, TransactionResponse transaction, Instant createdAt) {}
