package com.finanzbuddy.api.document.port.out;

import com.finanzbuddy.api.document.domain.model.Document;
import com.finanzbuddy.api.document.domain.model.DocumentStatus;
import com.finanzbuddy.api.document.domain.model.DocumentType;
import com.finanzbuddy.api.document.domain.model.PageRequest;
import com.finanzbuddy.api.document.domain.model.PageResult;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentRepositoryPort {

    Optional<Long> findOwnerIdByEmail(String email);

    Document createPending(long ownerId, String originalFilename, DocumentType type);

    Optional<Document> findById(long documentId);

    Optional<Document> findByIdAndOwnerId(long documentId, long ownerId);

    /**
     * Persists mutable fields (storage path, status, error message) of an existing document. Throws
     * {@link com.finanzbuddy.api.document.domain.exception.ConcurrentDocumentModificationException}
     * when a concurrent update is detected.
     */
    Document save(Document document);

    void deleteById(long documentId);

    /**
     * Finds documents in the given status with a null storage path created before {@code cutoff} —
     * the signature of an upload whose metadata transaction never completed. Domain-paged.
     */
    List<Document> findStalePending(DocumentStatus status, Instant cutoff, PageRequest pageRequest);

    /**
     * Deletes a document only if it is still stale (same status, still no storage path), so an
     * upload that finalizes between a scan and its delete is not removed. Returns the number of
     * rows deleted (0 or 1).
     */
    int deleteByIdIfStale(long documentId, DocumentStatus status);

    PageResult<Document> findByOwner(long ownerId, PageRequest pageRequest);

    long countByOwner(long ownerId);

    long countByOwnerAndStatus(long ownerId, DocumentStatus status);

    long countByOwnerAndStatusIn(long ownerId, Collection<DocumentStatus> statuses);
}
