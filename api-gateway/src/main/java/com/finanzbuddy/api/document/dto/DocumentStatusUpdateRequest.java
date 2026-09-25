package com.finanzbuddy.api.document.dto;

import com.finanzbuddy.api.entity.DocumentStatus;
import jakarta.validation.constraints.NotNull;

public record DocumentStatusUpdateRequest(@NotNull DocumentStatus status, String message) {}
