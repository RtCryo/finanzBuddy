package com.finanzbuddy.api.cleanup.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.finanzbuddy.api.cleanup.port.out.ClockPort;
import com.finanzbuddy.api.document.domain.model.Document;
import com.finanzbuddy.api.document.domain.model.DocumentStatus;
import com.finanzbuddy.api.document.domain.model.PageRequest;
import com.finanzbuddy.api.document.domain.model.PageResult;
import com.finanzbuddy.api.document.domain.model.DocumentType;
import com.finanzbuddy.api.document.port.out.DocumentRepositoryPort;
import com.finanzbuddy.api.document.port.out.FileStoragePort;
import com.finanzbuddy.api.document.port.out.TransactionRunnerPort;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class StalePendingReconciliationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Duration STALE_AFTER = Duration.ofHours(1);

    private final FakeDocumentRepository repository = new FakeDocumentRepository();
    private final RecordingFileStorage fileStorage = new RecordingFileStorage();
    private final TransactionRunnerPort tx = Supplier::get;
    private final ClockPort clock = () -> NOW;

    private StalePendingReconciliation reconciliation() {
        return new StalePendingReconciliation(
                repository, fileStorage, tx, clock, STALE_AFTER, 500);
    }

    @Test
    void staleNoStoragePathPendingDocumentIsDeletedAndObjectRemoved() {
        Document stale =
                pending(1L, 7L, "data.csv", null, NOW.minus(Duration.ofHours(2)));
        repository.add(stale);

        reconciliation().reconcile();

        assertThat(repository.documents).doesNotContainKey(1L);
        assertThat(fileStorage.removed).containsExactly("users/7/1-data.csv");
    }

    @Test
    void documentFinalizedBetweenScanAndDeleteIsNotDeleted() {
        Document selected =
                pending(1L, 7L, "data.csv", null, NOW.minus(Duration.ofHours(2)));
        repository.add(selected);
        // Simulate the upload finalizing (storage path set) after selection but before the
        // conditional delete: the row is no longer stale, so deleteByIdIfStale must be a no-op.
        repository.finalizeBeforeDelete(1L, "users/7/1-data.csv");

        reconciliation().reconcile();

        assertThat(repository.documents).containsKey(1L);
        assertThat(fileStorage.removed).isEmpty();
    }

    @Test
    void storageRemovalFailureIsSwallowedAndPassContinues() {
        repository.add(pending(1L, 7L, "a.csv", null, NOW.minus(Duration.ofHours(2))));
        repository.add(pending(2L, 7L, "b.csv", null, NOW.minus(Duration.ofHours(2))));
        fileStorage.failOn("users/7/1-a.csv");

        assertThatCode(() -> reconciliation().reconcile()).doesNotThrowAnyException();

        assertThat(repository.documents).doesNotContainKey(1L).doesNotContainKey(2L);
        assertThat(fileStorage.removed).contains("users/7/2-b.csv");
    }

    @Test
    void freshPendingDocumentWithinThresholdIsNotSelected() {
        repository.add(pending(1L, 7L, "data.csv", null, NOW.minus(Duration.ofMinutes(30))));

        reconciliation().reconcile();

        assertThat(repository.documents).containsKey(1L);
        assertThat(fileStorage.removed).isEmpty();
    }

    private static Document pending(
            long id, long ownerId, String filename, String storagePath, Instant createdAt) {
        return new Document(
                id,
                ownerId,
                filename,
                storagePath,
                DocumentType.CSV.name(),
                DocumentStatus.PENDING,
                null,
                createdAt);
    }

    private static final class FakeDocumentRepository implements DocumentRepositoryPort {
        private final Map<Long, Document> documents = new LinkedHashMap<>();
        private final Map<Long, String> finalizeBeforeDelete = new LinkedHashMap<>();

        void add(Document document) {
            documents.put(document.id(), document);
        }

        void finalizeBeforeDelete(long id, String storagePath) {
            finalizeBeforeDelete.put(id, storagePath);
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
            String finalized = finalizeBeforeDelete.remove(documentId);
            if (finalized != null) {
                documents.computeIfPresent(
                        documentId, (id, doc) -> doc.withStoragePath(finalized));
            }
            Document doc = documents.get(documentId);
            if (doc != null && doc.status() == status && doc.storagePath() == null) {
                documents.remove(documentId);
                return 1;
            }
            return 0;
        }

        @Override
        public Optional<Long> findOwnerIdByEmail(String email) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Document createPending(long ownerId, String originalFilename, DocumentType type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Document> findById(long documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Document> findByIdAndOwnerId(long documentId, long ownerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Document save(Document document) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(long documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PageResult<Document> findByOwner(long ownerId, PageRequest pageRequest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByOwner(long ownerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByOwnerAndStatus(long ownerId, DocumentStatus status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByOwnerAndStatusIn(long ownerId, Collection<DocumentStatus> statuses) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingFileStorage implements FileStoragePort {
        private final List<String> removed = new ArrayList<>();
        private String failPath;

        void failOn(String storagePath) {
            this.failPath = storagePath;
        }

        @Override
        public void remove(String storagePath) {
            if (storagePath.equals(failPath)) {
                throw new RuntimeException("storage unreachable");
            }
            removed.add(storagePath);
        }

        @Override
        public String store(
                byte[] content,
                String contentType,
                long ownerId,
                long documentId,
                String filename) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] read(String storagePath) {
            throw new UnsupportedOperationException();
        }
    }
}
