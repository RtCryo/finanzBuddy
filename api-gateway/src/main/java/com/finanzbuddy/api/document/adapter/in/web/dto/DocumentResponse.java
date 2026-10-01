package com.finanzbuddy.api.document.adapter.in.web.dto;

import com.finanzbuddy.api.document.domain.model.DocumentStatus;
import java.time.Instant;

public record DocumentResponse(
        Long id,
        String originalFilename,
        String type,
        DocumentStatus status,
        Instant createdAt,
        String errorMessage) {}
