package com.finanzbuddy.api.document.port.out;

public interface FileStoragePort {

    /**
     * Stores the content under a deterministic key derived from owner, document, and filename.
     * Returns the storage path. Throws {@link
     * com.finanzbuddy.api.document.domain.exception.FileStorageException} on failure.
     */
    String store(
            byte[] content, String contentType, long ownerId, long documentId, String filename);

    /** Reads the stored object. Throws {@code FileStorageException} on failure. */
    byte[] read(String storagePath);

    /** Best-effort removal; implementations must not throw if the object is missing. */
    void remove(String storagePath);
}
