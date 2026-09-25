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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final MinioClient minioClient;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Value("${app.minio.bucket}")
    private String bucket;

    @Value("${app.kafka.document-topic-prefix}")
    private String topicPrefix;

    public DocumentService(
            MinioClient minioClient,
            DocumentRepository documentRepository,
            UserRepository userRepository,
            KafkaTemplate<Object, Object> kafkaTemplate) {
        this.minioClient = minioClient;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public DocumentResponse uploadDocument(String userEmail, MultipartFile file, String type) {
        DocumentType documentType = parseAndValidateType(type);
        User user =
                userRepository
                        .findByEmail(userEmail)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "User not found"));

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Original filename is required");
        }

        Document document = new Document();
        document.setUser(user);
        document.setOriginalFilename(originalFilename);
        document.setType(documentType.name());
        document.setStatus(DocumentStatus.PENDING);
        document = documentRepository.save(document);

        String storagePath = buildStoragePath(user.getId(), document.getId(), originalFilename);
        document.setStoragePath(storagePath);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder().bucket(bucket).object(storagePath).stream(
                                    inputStream, file.getSize(), -1)
                            .contentType(
                                    file.getContentType() != null
                                            ? file.getContentType()
                                            : "application/octet-stream")
                            .build());
        } catch (Exception e) {
            log.error("Failed to store file in MinIO", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }

        Document finalDocument = document;
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        publishDocumentUploadedEvent(finalDocument);
                    }
                });

        return toResponse(document);
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
        if (current == DocumentStatus.PROCESSED && next == DocumentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status transition");
        }
        if (current == DocumentStatus.FAILED && next == DocumentStatus.PROCESSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status transition");
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
        kafkaTemplate
                .send(topic, event)
                .whenComplete(
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
