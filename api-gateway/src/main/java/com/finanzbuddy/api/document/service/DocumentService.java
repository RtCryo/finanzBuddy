package com.finanzbuddy.api.document.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finanzbuddy.api.document.DocumentStorage;
import com.finanzbuddy.api.document.DocumentType;
import com.finanzbuddy.api.document.dto.DocumentResponse;
import com.finanzbuddy.api.document.dto.DocumentStatusUpdateRequest;
import com.finanzbuddy.api.entity.Document;
import com.finanzbuddy.api.entity.DocumentStatus;
import com.finanzbuddy.api.entity.OutboxEvent;
import com.finanzbuddy.api.entity.User;
import com.finanzbuddy.api.event.DocumentUploadedEvent;
import com.finanzbuddy.api.repository.DocumentRepository;
import com.finanzbuddy.api.repository.OutboxEventRepository;
import com.finanzbuddy.api.repository.UserRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final Set<String> ALLOWED_TRANSITIONS =
            Set.of(
                    "PENDING->PROCESSING",
                    "PROCESSING->PROCESSED",
                    "PROCESSING->FAILED",
                    "FAILED->PENDING",
                    "PENDING->FAILED");

    private final MinioClient minioClient;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;

    @Value("${app.minio.bucket}")
    private String bucket;

    @Value("${app.kafka.document-topic-prefix}")
    private String topicPrefix;

    @Value("${app.upload.accepted-types:CSV}")
    private Set<DocumentType> acceptedTypes;

    public DocumentService(
            MinioClient minioClient,
            DocumentRepository documentRepository,
            UserRepository userRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            PlatformTransactionManager txManager) {
        this.minioClient = minioClient;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    public DocumentResponse uploadDocument(String userEmail, MultipartFile file, String type) {
        DocumentType documentType = parseAndValidateType(type);

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
        }

        // MultipartFile streams are not guaranteed re-readable, so buffer the content once (bounded
        // by the multipart max-file-size) and reuse the same bytes for validation and storage.
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unable to read uploaded file");
        }
        validateContentMatchesType(documentType, content);

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Original filename is required");
        }

        // TX1: persist initial record to obtain document ID; short transaction, released before I/O
        Document pending =
                txTemplate.execute(
                        status -> {
                            User user =
                                    userRepository
                                            .findByEmail(userEmail)
                                            .orElseThrow(
                                                    () ->
                                                            new ResponseStatusException(
                                                                    HttpStatus.UNAUTHORIZED,
                                                                    "User not found"));
                            Document doc = new Document();
                            doc.setUser(user);
                            doc.setOriginalFilename(originalFilename);
                            doc.setType(documentType.name());
                            doc.setStatus(DocumentStatus.PENDING);
                            return documentRepository.save(doc);
                        });

        Long userId = pending.getUser().getId();
        Long docId = pending.getId();
        String storagePath = DocumentStorage.objectKey(userId, docId, originalFilename);

        // MinIO upload outside any transaction — no DB connection held during I/O
        try {
            minioClient.putObject(
                    PutObjectArgs.builder().bucket(bucket).object(storagePath).stream(
                                    new ByteArrayInputStream(content), content.length, -1)
                            .contentType(
                                    file.getContentType() != null
                                            ? file.getContentType()
                                            : "application/octet-stream")
                            .build());
        } catch (Exception e) {
            log.error(
                    "Failed to store file in MinIO for document {}, cleaning up record", docId, e);
            deletePendingRecord(docId);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }

        // TX2: persist storage path and enqueue the upload event in the same transaction so the
        // event cannot be lost once the document is committed (transactional outbox pattern).
        try {
            return txTemplate.execute(
                    status -> {
                        Document doc =
                                documentRepository
                                        .findById(docId)
                                        .orElseThrow(
                                                () ->
                                                        new ResponseStatusException(
                                                                HttpStatus.INTERNAL_SERVER_ERROR,
                                                                "Document record lost after"
                                                                        + " upload"));
                        doc.setStoragePath(storagePath);
                        Document saved = documentRepository.save(doc);
                        enqueueUploadedEvent(saved);
                        return toResponse(saved);
                    });
        } catch (RuntimeException e) {
            // The file is already in MinIO but finalization failed; roll back the orphaned state.
            log.error("Failed to finalize upload for document {}, cleaning up", docId, e);
            removeStoredObject(storagePath);
            deletePendingRecord(docId);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to finalize upload");
        }
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> listDocuments(String userEmail, Pageable pageable) {
        User user = getUser(userEmail);
        return documentRepository.findByUserId(user.getId(), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(String userEmail, Long documentId) {
        User user = getUser(userEmail);
        Document document =
                documentRepository
                        .findByIdAndUserId(documentId, user.getId())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Document not found"));
        return toResponse(document);
    }

    /**
     * Updates a document's status on behalf of an internal parser service. Unlike the user-facing
     * query endpoints this is not scoped to a document owner: parser services authenticate with a
     * service API key and reference documents by id only.
     */
    @Transactional
    public DocumentResponse updateDocumentStatus(
            Long documentId, DocumentStatusUpdateRequest request) {
        Document document =
                documentRepository
                        .findById(documentId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getStatus() == request.status()) {
            // Idempotent redelivery of the current status from an at-least-once parser: return 200
            // instead of a 400 that would push the parser into a retry loop, and do not re-enqueue.
            // A repeated FAILED report may carry a more accurate reason, so refresh the message.
            if (request.status() == DocumentStatus.FAILED) {
                document.setErrorMessage(request.message());
            }
            return toResponse(document);
        }

        validateStatusTransition(document.getStatus(), request.status());
        document.setStatus(request.status());
        // Clear any stale failure reason on non-FAILED transitions (e.g. FAILED->PENDING reprocess)
        // so a later success never keeps showing an old error message.
        document.setErrorMessage(
                request.status() == DocumentStatus.FAILED ? request.message() : null);
        // A reprocess (FAILED->PENDING) must re-publish the upload event in the same transaction;
        // otherwise the document would sit in PENDING with no consumer ever picking it up again.
        if (request.status() == DocumentStatus.PENDING) {
            enqueueUploadedEvent(document);
        }
        return toResponse(document);
    }

    private void enqueueUploadedEvent(Document document) {
        String topic = topicPrefix + "." + document.getType().toLowerCase();
        DocumentUploadedEvent event =
                new DocumentUploadedEvent(
                        document.getId(),
                        document.getUser().getId(),
                        document.getType(),
                        document.getStoragePath());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize DocumentUploadedEvent for document " + document.getId(),
                    e);
        }
        outboxEventRepository.save(new OutboxEvent(document.getId(), topic, payload));
    }

    private void removeStoredObject(String storagePath) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(storagePath).build());
        } catch (Exception e) {
            log.error("Failed to remove orphaned MinIO object {}", storagePath, e);
        }
    }

    private void deletePendingRecord(Long documentId) {
        try {
            txTemplate.executeWithoutResult(status -> documentRepository.deleteById(documentId));
        } catch (RuntimeException e) {
            log.error("Failed to delete orphaned document record {}", documentId, e);
        }
    }

    private void validateStatusTransition(DocumentStatus current, DocumentStatus next) {
        String transition = current.name() + "->" + next.name();
        if (!ALLOWED_TRANSITIONS.contains(transition)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid status transition: " + transition);
        }
    }

    private User getUser(String userEmail) {
        return userRepository
                .findByEmail(userEmail)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED, "User not found"));
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
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "File content does not match declared type PDF");
        }
    }

    private DocumentType parseAndValidateType(String type) {
        if (type == null || type.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document type is required");
        }
        DocumentType parsed;
        try {
            parsed = DocumentType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported document type: " + type);
        }
        if (!acceptedTypes.contains(parsed)) {
            // PDF/XML are recognised types but not consumable in the MVP (parser support deferred),
            // so reject them here rather than let uploads sit PENDING with no consumer.
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Document type not accepted: " + parsed);
        }
        return parsed;
    }

    private DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getOriginalFilename(),
                document.getType(),
                document.getStatus(),
                document.getCreatedAt());
    }
}
