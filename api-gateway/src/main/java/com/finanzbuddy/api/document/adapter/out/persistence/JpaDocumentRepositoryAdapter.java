package com.finanzbuddy.api.document.adapter.out.persistence;

import com.finanzbuddy.api.document.domain.exception.ConcurrentDocumentModificationException;
import com.finanzbuddy.api.document.domain.model.Document;
import com.finanzbuddy.api.document.domain.model.DocumentStatus;
import com.finanzbuddy.api.document.domain.model.DocumentType;
import com.finanzbuddy.api.document.domain.model.PageRequest;
import com.finanzbuddy.api.document.domain.model.PageResult;
import com.finanzbuddy.api.document.port.out.DocumentRepositoryPort;
import com.finanzbuddy.api.repository.UserRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class JpaDocumentRepositoryAdapter implements DocumentRepositoryPort {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    public JpaDocumentRepositoryAdapter(
            DocumentRepository documentRepository, UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Optional<Long> findOwnerIdByEmail(String email) {
        return userRepository.findByEmail(email).map(user -> user.getId());
    }

    @Override
    public Document createPending(long ownerId, String originalFilename, DocumentType type) {
        DocumentEntity entity = new DocumentEntity();
        entity.setUser(userRepository.getReferenceById(ownerId));
        entity.setOriginalFilename(originalFilename);
        entity.setType(type.name());
        entity.setStatus(DocumentStatus.PENDING);
        return toDomain(documentRepository.save(entity));
    }

    @Override
    public Optional<Document> findById(long documentId) {
        return documentRepository.findById(documentId).map(this::toDomain);
    }

    @Override
    public Optional<Document> findByIdAndOwnerId(long documentId, long ownerId) {
        return documentRepository.findByIdAndUserId(documentId, ownerId).map(this::toDomain);
    }

    @Override
    public Document save(Document document) {
        DocumentEntity entity =
                documentRepository
                        .findById(document.id())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Document " + document.id() + " no longer exists"));
        entity.setStoragePath(document.storagePath());
        entity.setStatus(document.status());
        entity.setErrorMessage(document.errorMessage());
        try {
            return toDomain(documentRepository.saveAndFlush(entity));
        } catch (OptimisticLockingFailureException e) {
            // Two writers updated the same document concurrently; surface a domain signal the web
            // adapter maps to 409 so the caller can retry rather than see a 500.
            throw new ConcurrentDocumentModificationException(
                    "Document status was modified concurrently");
        }
    }

    @Override
    public void deleteById(long documentId) {
        documentRepository.deleteById(documentId);
    }

    @Override
    public List<Document> findStalePending(
            DocumentStatus status, Instant cutoff, PageRequest pageRequest) {
        return documentRepository
                .findByStatusAndStoragePathIsNullAndCreatedAtBefore(
                        status,
                        cutoff,
                        org.springframework.data.domain.PageRequest.of(
                                pageRequest.page(), pageRequest.size()))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public int deleteByIdIfStale(long documentId, DocumentStatus status) {
        return documentRepository.deleteByIdIfStale(documentId, status);
    }

    @Override
    public PageResult<Document> findByOwner(long ownerId, PageRequest pageRequest) {
        Pageable pageable =
                org.springframework.data.domain.PageRequest.of(
                        pageRequest.page(), pageRequest.size());
        String query =
                pageRequest.filenameFilter() == null ? "" : pageRequest.filenameFilter().trim();
        Page<DocumentEntity> page =
                query.isEmpty()
                        ? documentRepository.findByUserId(ownerId, pageable)
                        : documentRepository.findByUserIdAndOriginalFilenameContainingIgnoreCase(
                                ownerId, query, pageable);
        return new PageResult<>(
                page.getContent().stream().map(this::toDomain).toList(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize());
    }

    @Override
    public long countByOwner(long ownerId) {
        return documentRepository.countByUserId(ownerId);
    }

    @Override
    public long countByOwnerAndStatus(long ownerId, DocumentStatus status) {
        return documentRepository.countByUserIdAndStatus(ownerId, status);
    }

    @Override
    public long countByOwnerAndStatusIn(long ownerId, Collection<DocumentStatus> statuses) {
        return documentRepository.countByUserIdAndStatusIn(ownerId, statuses);
    }

    private Document toDomain(DocumentEntity entity) {
        return new Document(
                entity.getId(),
                entity.getUser().getId(),
                entity.getOriginalFilename(),
                entity.getStoragePath(),
                entity.getType(),
                entity.getStatus(),
                entity.getErrorMessage(),
                entity.getCreatedAt());
    }
}
