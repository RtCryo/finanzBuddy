package com.finanzbuddy.api.document.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.finanzbuddy.api.document.domain.exception.ConcurrentDocumentModificationException;
import com.finanzbuddy.api.document.domain.exception.DocumentNotFoundException;
import com.finanzbuddy.api.document.domain.exception.InvalidDocumentRequestException;
import com.finanzbuddy.api.document.domain.model.Document;
import com.finanzbuddy.api.document.domain.model.DocumentStatus;
import com.finanzbuddy.api.document.domain.model.DocumentType;
import com.finanzbuddy.api.document.domain.model.PageRequest;
import com.finanzbuddy.api.document.domain.model.PageResult;
import com.finanzbuddy.api.document.domain.model.UploadDocumentCommand;
import com.finanzbuddy.api.document.port.out.DocumentEventPort;
import com.finanzbuddy.api.document.port.out.DocumentRepositoryPort;
import com.finanzbuddy.api.document.port.out.FileStoragePort;
import com.finanzbuddy.api.document.port.out.TransactionRunnerPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class DocumentServiceTest {

    private final FakeDocumentRepository documentRepository = new FakeDocumentRepository();
    private final FakeFileStorage fileStorage = new FakeFileStorage();
    private final RecordingEventPort eventPort = new RecordingEventPort();
    private final TransactionRunnerPort tx = Supplier::get;

    private DocumentService service(DocumentType... accepted) {
        return new DocumentService(
                documentRepository,
                documentId -> List.of(),
                fileStorage,
                eventPort,
                tx,
                Set.of(accepted));
    }

    @Test
    void uploadStoresFilePersistsAndEnqueuesEvent() {
        documentRepository.owners.put("owner@example.com", 7L);

        Document result =
                service(DocumentType.CSV)
                        .upload(
                                new UploadDocumentCommand(
                                        "a,b\n1,2".getBytes(),
                                        "data.csv",
                                        "text/csv",
                                        DocumentType.CSV,
                                        "owner@example.com"));

        assertThat(result.id()).isNotNull();
        assertThat(result.storagePath()).isNotNull();
        assertThat(result.status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(eventPort.enqueued).hasSize(1);
    }

    @Test
    void uploadRejectsTypeNotAccepted() {
        documentRepository.owners.put("owner@example.com", 7L);

        assertThatExceptionOfType(InvalidDocumentRequestException.class)
                .isThrownBy(
                        () ->
                                service(DocumentType.CSV)
                                        .upload(
                                                new UploadDocumentCommand(
                                                        "%PDF-1.4".getBytes(),
                                                        "data.pdf",
                                                        "application/pdf",
                                                        DocumentType.PDF,
                                                        "owner@example.com")));
        assertThat(eventPort.enqueued).isEmpty();
    }

    @Test
    void getRejectsForeignDocumentWith404() {
        documentRepository.owners.put("owner@example.com", 7L);

        assertThatExceptionOfType(DocumentNotFoundException.class)
                .isThrownBy(() -> service(DocumentType.CSV).get("owner@example.com", 999L));
    }

    @Test
    void updateStatusRejectsInvalidTransition() {
        long id = documentRepository.insert(7L, DocumentStatus.PENDING);

        assertThatExceptionOfType(InvalidDocumentRequestException.class)
                .isThrownBy(
                        () ->
                                service(DocumentType.CSV)
                                        .updateStatus(id, DocumentStatus.PROCESSED, null));
    }

    @Test
    void updateStatusPropagatesConcurrentModification() {
        long id = documentRepository.insert(7L, DocumentStatus.PENDING);
        documentRepository.failSaveWithConflict = true;

        assertThatExceptionOfType(ConcurrentDocumentModificationException.class)
                .isThrownBy(
                        () ->
                                service(DocumentType.CSV)
                                        .updateStatus(id, DocumentStatus.PROCESSING, null));
    }

    @Test
    void reprocessToPendingEnqueuesNewEvent() {
        long id = documentRepository.insert(7L, DocumentStatus.FAILED);

        service(DocumentType.CSV).updateStatus(id, DocumentStatus.PENDING, null);

        assertThat(eventPort.enqueued).hasSize(1);
    }

    private static final class FakeDocumentRepository implements DocumentRepositoryPort {
        private final Map<String, Long> owners = new HashMap<>();
        private final Map<Long, Document> documents = new HashMap<>();
        private long sequence = 0;
        private boolean failSaveWithConflict;

        long insert(long ownerId, DocumentStatus status) {
            long id = ++sequence;
            documents.put(
                    id,
                    new Document(
                            id,
                            ownerId,
                            "data.csv",
                            "users/x",
                            "CSV",
                            status,
                            null,
                            Instant.now()));
            return id;
        }

        @Override
        public Optional<Long> findOwnerIdByEmail(String email) {
            return Optional.ofNullable(owners.get(email));
        }

        @Override
        public Document createPending(long ownerId, String originalFilename, DocumentType type) {
            long id = ++sequence;
            Document doc =
                    new Document(
                            id,
                            ownerId,
                            originalFilename,
                            null,
                            type.name(),
                            DocumentStatus.PENDING,
                            null,
                            Instant.now());
            documents.put(id, doc);
            return doc;
        }

        @Override
        public Optional<Document> findById(long documentId) {
            return Optional.ofNullable(documents.get(documentId));
        }

        @Override
        public Optional<Document> findByIdAndOwnerId(long documentId, long ownerId) {
            return findById(documentId).filter(doc -> doc.ownerId() == ownerId);
        }

        @Override
        public Document save(Document document) {
            if (failSaveWithConflict) {
                throw new ConcurrentDocumentModificationException(
                        "Document status was modified concurrently");
            }
            documents.put(document.id(), document);
            return document;
        }

        @Override
        public void deleteById(long documentId) {
            documents.remove(documentId);
        }

        @Override
        public List<Document> findStalePending(
                DocumentStatus status, Instant cutoff, PageRequest pageRequest) {
            return documents.values().stream()
                    .filter(d -> d.status() == status)
                    .filter(d -> d.storagePath() == null)
                    .filter(d -> d.createdAt().isBefore(cutoff))
                    .limit(pageRequest.size())
                    .toList();
        }

        @Override
        public int deleteByIdIfStale(long documentId, DocumentStatus status) {
            Document doc = documents.get(documentId);
            if (doc != null && doc.status() == status && doc.storagePath() == null) {
                documents.remove(documentId);
                return 1;
            }
            return 0;
        }

        @Override
        public PageResult<Document> findByOwner(long ownerId, PageRequest pageRequest) {
            List<Document> items = new ArrayList<>(documents.values());
            return new PageResult<>(items, items.size(), 1, 0, items.size());
        }

        @Override
        public long countByOwner(long ownerId) {
            return documents.size();
        }

        @Override
        public long countByOwnerAndStatus(long ownerId, DocumentStatus status) {
            return documents.values().stream().filter(d -> d.status() == status).count();
        }

        @Override
        public long countByOwnerAndStatusIn(long ownerId, Collection<DocumentStatus> statuses) {
            return documents.values().stream().filter(d -> statuses.contains(d.status())).count();
        }
    }

    private static final class FakeFileStorage implements FileStoragePort {
        @Override
        public String store(
                byte[] content,
                String contentType,
                long ownerId,
                long documentId,
                String filename) {
            return "users/" + ownerId + "/" + documentId + "-" + filename;
        }

        @Override
        public byte[] read(String storagePath) {
            return new byte[0];
        }

        @Override
        public void remove(String storagePath) {}
    }

    private static final class RecordingEventPort implements DocumentEventPort {
        private final List<Document> enqueued = new ArrayList<>();

        @Override
        public void enqueueUploadedEvent(Document document) {
            enqueued.add(document);
        }
    }
}
