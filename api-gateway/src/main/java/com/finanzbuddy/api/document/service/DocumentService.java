package com.finanzbuddy.api.document.service;

import com.finanzbuddy.api.document.DocumentType;
import com.finanzbuddy.api.document.dto.DocumentResponse;
import com.finanzbuddy.api.document.dto.DocumentStatusUpdateRequest;
import com.finanzbuddy.api.entity.Document;
import com.finanzbuddy.api.entity.DocumentStatus;
import com.finanzbuddy.api.entity.User;
import com.finanzbuddy.api.event.DocumentUploadedEvent;
import com.finanzbuddy.api.repository.DocumentRepository;
import com.finanzbuddy.api.repository.UserRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final TransactionTemplate txTemplate;

    @Value("${app.minio.bucket}")
    private String bucket;

    @Value("${app.kafka.document-topic-prefix}")
    private String topicPrefix;

    public DocumentService(
            MinioClient minioClient,
            DocumentRepository documentRepository,
            UserRepository userRepository,
            KafkaTemplate<Object, Object> kafkaTemplate,
            PlatformTransactionManager txManager) {
        this.minioClient = minioClient;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    public DocumentResponse uploadDocument(String userEmail, MultipartFile file, String type) {
        DocumentType documentType = parseAndValidateType(type);

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
        String storagePath = buildStoragePath(userId, pending.getId(), originalFilename);

        // MinIO upload outside any transaction — no DB connection held during I/O
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(storagePath)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(
                                    file.getContentType() != null
                                            ? file.getContentType()
                                            : "application/octet-stream")
                            .build());
        } catch (Exception e) {
            log.error(
                    "Failed to store file in MinIO for document {}, cleaning up record",
                    pending.getId(),
                    e);
            documentRepository.deleteById(pending.getId());
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }

        // TX2: persist storage path and fire Kafka event on commit
        Long docId = pending.getId();
        return txTemplate.execute(
                status -> {
                    Document doc =
                            documentRepository
                                    .findById(docId)
                                    .orElseThrow(
                                            () ->
                                                    new ResponseStatusException(
                                                            HttpStatus.INTERNAL_SERVER_ERROR,
                                                            "Document record lost after upload"));
                    doc.setStoragePath(storagePath);
                    Document saved = documentRepository.save(doc);
                    TransactionSynchronizationManager.registerSynchronization(
                            new TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    publishDocumentUploadedEvent(saved);
                                }
                            });
                    return toResponse(saved);
                });
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

    @Transactional
    public DocumentResponse updateDocumentStatus(
            String userEmail, Long documentId, DocumentStatusUpdateRequest request) {
        User user = getUser(userEmail);
        Document document =
                documentRepository
                        .findByIdAndUserId(documentId, user.getId())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Document not found"));

        validateStatusTransition(document.getStatus(), request.status());
        document.setStatus(request.status());
        return toResponse(document);
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

    private void publishDocumentUploadedEvent(Document document) {
        String topic = topicPrefix + "." + document.getType().toLowerCase();
        DocumentUploadedEvent event =
                new DocumentUploadedEvent(
                        document.getId(),
                        document.getUser().getId(),
                        document.getType(),
                        document.getStoragePath());
        var future = kafkaTemplate.send(topic, event);
        if (future == null) {
            log.warn(
                    "Kafka send() returned null for topic {} document {}",
                    topic,
                    document.getId());
            return;
        }
        future.whenComplete(
                (result, ex) -> {
                    if (ex != null) {
                        log.error(
                                "Failed to deliver event to topic {} for document {}",
                                topic,
                                document.getId(),
                                ex);
                    } else {
                        log.info(
                                "Published event to topic {} for document {}",
                                topic,
                                document.getId());
                    }
                });
    }

    private DocumentType parseAndValidateType(String type) {
        if (type == null || type.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document type is required");
        }
        try {
            return DocumentType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported document type: " + type);
        }
    }

    private String buildStoragePath(Long userId, Long documentId, String originalFilename) {
        String sanitized = Paths.get(originalFilename).getFileName().toString();
        return String.format("users/%d/%d-%s", userId, documentId, sanitized);
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
