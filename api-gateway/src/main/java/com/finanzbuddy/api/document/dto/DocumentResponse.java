package com.finanzbuddy.api.document.dto;

import com.finanzbuddy.api.entity.DocumentStatus;
import java.time.Instant;

public record DocumentResponse(
        Long id, String originalFilename, String type, DocumentStatus status, Instant createdAt) {}
