package com.finanzbuddy.api.document.domain.model;

public record UploadDocumentCommand(
        byte[] content,
        String filename,
        String contentType,
        DocumentType type,
        String ownerEmail) {}
