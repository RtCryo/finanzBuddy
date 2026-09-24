package com.finanzbuddy.api.event;

public record DocumentUploadedEvent(
        Long documentId, Long userId, String type, String storagePath) {}
