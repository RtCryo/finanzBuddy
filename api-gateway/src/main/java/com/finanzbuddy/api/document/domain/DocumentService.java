package com.finanzbuddy.api.document.domain;

import com.finanzbuddy.api.document.domain.exception.DocumentNotFoundException;
import com.finanzbuddy.api.document.domain.exception.DownloadUnavailableException;
import com.finanzbuddy.api.document.domain.exception.FileStorageException;
import com.finanzbuddy.api.document.domain.exception.InvalidDocumentRequestException;
import com.finanzbuddy.api.document.domain.exception.UnauthorizedDocumentAccessException;
import com.finanzbuddy.api.document.domain.exception.UploadFailedException;
import com.finanzbuddy.api.document.domain.model.Document;
import com.finanzbuddy.api.document.domain.model.DocumentListResult;
import com.finanzbuddy.api.document.domain.model.DocumentStatus;
import com.finanzbuddy.api.document.domain.model.DocumentType;
import com.finanzbuddy.api.document.domain.model.DownloadedFile;
import com.finanzbuddy.api.document.domain.model.PageRequest;
import com.finanzbuddy.api.document.domain.model.PageResult;
import com.finanzbuddy.api.document.domain.model.Transaction;
import com.finanzbuddy.api.document.domain.model.UploadDocumentCommand;
import com.finanzbuddy.api.document.port.in.DownloadOriginalFileUseCase;
import com.finanzbuddy.api.document.port.in.GetDocumentUseCase;
import com.finanzbuddy.api.document.port.in.ListDocumentsUseCase;
import com.finanzbuddy.api.document.port.in.ListTransactionsUseCase;
import com.finanzbuddy.api.document.port.in.UpdateDocumentStatusUseCase;
import com.finanzbuddy.api.document.port.in.UploadDocumentUseCase;
import com.finanzbuddy.api.document.port.out.DocumentEventPort;
import com.finanzbuddy.api.document.port.out.DocumentRepositoryPort;
import com.finanzbuddy.api.document.port.out.FileStoragePort;
import com.finanzbuddy.api.document.port.out.TransactionReadPort;
import com.finanzbuddy.api.document.port.out.TransactionRunnerPort;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentService
        implements UploadDocumentUseCase,
                ListDocumentsUseCase,
                GetDocumentUseCase,
                ListTransactionsUseCase,
                DownloadOriginalFileUseCase,
                UpdateDocumentStatusUseCase {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final Set<String> ALLOWED_TRANSITIONS =
            Set.of(
                    "PENDING->PROCESSING",
                    "PROCESSING->PROCESSED",
                    "PROCESSING->FAILED",
                    "FAILED->PENDING",
                    "PENDING->FAILED");

    private final DocumentRepositoryPort documentRepository;
    private final TransactionReadPort transactionRead;
    private final FileStoragePort fileStorage;
    private final DocumentEventPort documentEvent;
    private final TransactionRunnerPort tx;
    private final Set<DocumentType> acceptedTypes;

    public DocumentService(
            DocumentRepositoryPort documentRepository,
            TransactionReadPort transactionRead,
            FileStoragePort fileStorage,
            DocumentEventPort documentEvent,
            TransactionRunnerPort tx,
            Set<DocumentType> acceptedTypes) {
        this.documentRepository = documentRepository;
        this.transactionRead = transactionRead;
        this.fileStorage = fileStorage;
        this.documentEvent = documentEvent;
        this.tx = tx;
        this.acceptedTypes = acceptedTypes;
    }

    @Override
    public Document upload(UploadDocumentCommand command) {
        DocumentType type = command.type();
        if (!acceptedTypes.contains(type)) {
            // PDF/XML are recognised types but not consumable in the MVP (parser support deferred),
            // so reject them here rather than let uploads sit PENDING with no consumer.
            throw new InvalidDocumentRequestException("Document type not accepted: " + type);
        }

        byte[] content = command.content();
        if (content == null || content.length == 0) {
            throw new InvalidDocumentRequestException("File must not be empty");
        }
        validateContentMatchesType(type, content);

        String originalFilename = command.filename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidDocumentRequestException("Original filename is required");
        }

        // TX1: persist initial record to obtain document ID; short transaction, released before I/O
        Document pending =
                tx.inTransaction(
                        () -> {
                            long ownerId =
                                    documentRepository
                                            .findOwnerIdByEmail(command.ownerEmail())
                                            .orElseThrow(
                                                    () ->
                                                            new UnauthorizedDocumentAccessException(
                                                                    "User not found"));
                            return documentRepository.createPending(
                                    ownerId, originalFilename, type);
                        });

        long ownerId = pending.ownerId();
        long docId = pending.id();

        // Object-storage upload outside any transaction — no DB connection held during I/O
        String storagePath;
        try {
            storagePath =
                    fileStorage.store(
                            content, command.contentType(), ownerId, docId, originalFilename);
        } catch (FileStorageException e) {
            log.error(
                    "Failed to store file in object storage for document {}, cleaning up record",
                    docId,
                    e);
            deletePendingRecord(docId);
            throw new UploadFailedException("Failed to store file");
        }

        // TX2: persist storage path and enqueue the upload event in the same transaction so the
        // event cannot be lost once the document is committed (transactional outbox pattern).
        try {
            String finalStoragePath = storagePath;
            return tx.inTransaction(
                    () -> {
                        Document doc =
                                documentRepository
                                        .findById(docId)
                                        .orElseThrow(
                                                () ->
                                                        new UploadFailedException(
                                                                "Document record lost after"
                                                                        + " upload"));
                        Document saved =
                                documentRepository.save(doc.withStoragePath(finalStoragePath));
                        documentEvent.enqueueUploadedEvent(saved);
                        return saved;
                    });
        } catch (RuntimeException e) {
            // The file is already stored but finalization failed; roll back the orphaned state.
            log.error("Failed to finalize upload for document {}, cleaning up", docId, e);
            removeStoredObject(storagePath);
            deletePendingRecord(docId);
            throw new UploadFailedException("Failed to finalize upload");
        }
    }

    @Override
    public DocumentListResult list(String ownerEmail, PageRequest pageRequest) {
        return tx.inTransaction(
                () -> {
                    long ownerId = resolveOwnerId(ownerEmail);
                    PageResult<Document> page =
                            documentRepository.findByOwner(ownerId, pageRequest);
                    return new DocumentListResult(
                            page,
                            documentRepository.countByOwner(ownerId),
                            documentRepository.countByOwnerAndStatus(
                                    ownerId, DocumentStatus.PROCESSED),
                            documentRepository.countByOwnerAndStatusIn(
                                    ownerId,
                                    List.of(DocumentStatus.PENDING, DocumentStatus.PROCESSING)));
                });
    }

    @Override
    public Document get(String ownerEmail, long documentId) {
        return tx.inTransaction(
                () -> {
                    long ownerId = resolveOwnerId(ownerEmail);
                    return documentRepository
                            .findByIdAndOwnerId(documentId, ownerId)
                            .orElseThrow(() -> new DocumentNotFoundException("Document not found"));
                });
    }

    @Override
    public List<Transaction> list(String ownerEmail, long documentId) {
        return tx.inTransaction(
                () -> {
                    long ownerId = resolveOwnerId(ownerEmail);
                    // Ownership guard: only surface transactions for a document the caller owns.
                    documentRepository
                            .findByIdAndOwnerId(documentId, ownerId)
                            .orElseThrow(() -> new DocumentNotFoundException("Document not found"));
                    return transactionRead.findByDocumentId(documentId);
                });
    }

    @Override
    public DownloadedFile download(String ownerEmail, long documentId) {
        StoredObject stored =
                tx.inTransaction(
                        () -> {
                            long ownerId = resolveOwnerId(ownerEmail);
                            Document document =
                                    documentRepository
                                            .findByIdAndOwnerId(documentId, ownerId)
                                            .orElseThrow(
                                                    () ->
                                                            new DocumentNotFoundException(
                                                                    "Document not found"));
                            String path = document.storagePath();
                            if (path == null || path.isBlank()) {
                                throw new DocumentNotFoundException(
                                        "Original file is not available");
                            }
                            return new StoredObject(path, document.originalFilename());
                        });

        try {
            byte[] content = fileStorage.read(stored.path());
            return new DownloadedFile(content, stored.filename());
        } catch (FileStorageException e) {
            log.error(
                    "Failed to read stored object {} for document {}",
                    stored.path(),
                    documentId,
                    e);
            throw new DownloadUnavailableException("Failed to read the original file");
        }
    }

    private record StoredObject(String path, String filename) {}

    @Override
    public Document updateStatus(long documentId, DocumentStatus newStatus, String message) {
        return tx.inTransaction(
                () -> {
                    Document document =
                            documentRepository
                                    .findById(documentId)
                                    .orElseThrow(
                                            () ->
                                                    new DocumentNotFoundException(
                                                            "Document not found"));

                    if (document.status() == newStatus) {
                        // Idempotent redelivery of the current status from an at-least-once parser:
                        // return 200 instead of a 400 that would push the parser into a retry loop,
                        // and do not re-enqueue. A repeated FAILED report may carry a more accurate
                        // reason, so refresh the message.
                        if (newStatus == DocumentStatus.FAILED) {
                            return documentRepository.save(document.withErrorMessage(message));
                        }
                        return document;
                    }

                    validateStatusTransition(document.status(), newStatus);
                    // Clear any stale failure reason on non-FAILED transitions (e.g.
                    // FAILED->PENDING
                    // reprocess) so a later success never keeps showing an old error message.
                    Document updated =
                            document.withStatus(newStatus)
                                    .withErrorMessage(
                                            newStatus == DocumentStatus.FAILED ? message : null);
                    Document saved = documentRepository.save(updated);
                    // A reprocess (FAILED->PENDING) must re-publish the upload event in the same
                    // transaction; otherwise the document would sit in PENDING with no consumer
                    // ever
                    // picking it up again.
                    if (newStatus == DocumentStatus.PENDING) {
                        documentEvent.enqueueUploadedEvent(saved);
                    }
                    return saved;
                });
    }

    private long resolveOwnerId(String ownerEmail) {
        return documentRepository
                .findOwnerIdByEmail(ownerEmail)
                .orElseThrow(() -> new UnauthorizedDocumentAccessException("User not found"));
    }

    private void removeStoredObject(String storagePath) {
        fileStorage.remove(storagePath);
    }

    private void deletePendingRecord(long documentId) {
        try {
            tx.inTransaction(
                    () -> {
                        documentRepository.deleteById(documentId);
                        return null;
                    });
        } catch (RuntimeException e) {
            log.error("Failed to delete orphaned document record {}", documentId, e);
        }
    }

    private void validateStatusTransition(DocumentStatus current, DocumentStatus next) {
        String transition = current.name() + "->" + next.name();
        if (!ALLOWED_TRANSITIONS.contains(transition)) {
            throw new InvalidDocumentRequestException("Invalid status transition: " + transition);
        }
    }

    private void validateContentMatchesType(DocumentType type, byte[] content) {
        // Defense-in-depth magic-byte check for binary types. CSV/XML are text and have no reliable
        // signature, so their content is validated downstream by the parser.
        if (type != DocumentType.PDF) {
            return;
        }
        if (content.length < 4
                || content[0] != '%'
                || content[1] != 'P'
                || content[2] != 'D'
                || content[3] != 'F') {
            throw new InvalidDocumentRequestException(
                    "File content does not match declared type PDF");
        }
    }
}
