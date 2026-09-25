package com.finanzbuddy.api.document;

import java.nio.file.Paths;

/** Builds the deterministic MinIO object key for a stored document. */
public final class DocumentStorage {

    private DocumentStorage() {}

    public static String objectKey(Long userId, Long documentId, String originalFilename) {
        String sanitized = Paths.get(originalFilename).getFileName().toString();
        return String.format("users/%d/%d-%s", userId, documentId, sanitized);
    }
}
