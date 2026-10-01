package com.finanzbuddy.api.document.domain.model;

import java.time.Instant;

public record Document(
        Long id,
        Long ownerId,
        String originalFilename,
        String storagePath,
        String type,
        DocumentStatus status,
        String errorMessage,
        Instant createdAt) {

    public Document withStoragePath(String newStoragePath) {
        return new Document(
                id,
                ownerId,
                originalFilename,
                newStoragePath,
                type,
                status,
                errorMessage,
                createdAt);
    }

    public Document withStatus(DocumentStatus newStatus) {
        return new Document(
                id,
                ownerId,
                originalFilename,
                storagePath,
                type,
                newStatus,
                errorMessage,
                createdAt);
    }

    public Document withErrorMessage(String newErrorMessage) {
        return new Document(
                id,
                ownerId,
                originalFilename,
                storagePath,
                type,
                status,
                newErrorMessage,
                createdAt);
    }
}
