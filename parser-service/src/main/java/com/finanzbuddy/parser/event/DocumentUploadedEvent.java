package com.finanzbuddy.parser.event;

public record DocumentUploadedEvent(
        Long documentId,
        Long userId,
        String type,
        String storagePath) {
}
