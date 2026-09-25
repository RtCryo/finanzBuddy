package com.finanzbuddy.api.document.cleanup;

import com.finanzbuddy.api.document.DocumentStorage;
import com.finanzbuddy.api.entity.DocumentStatus;
import com.finanzbuddy.api.repository.DocumentRepository;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Removes documents left in {@code PENDING} with no storage path — the signature of an upload whose
 * metadata transaction never completed (e.g. the JVM died between persisting the row and storing
 * the file). The MinIO object, if it was written, is deleted best-effort using the deterministic
 * path.
 */
@Component
public class StalePendingReconciler {

    private static final Logger log = LoggerFactory.getLogger(StalePendingReconciler.class);

    private final DocumentRepository documentRepository;
    private final MinioClient minioClient;
    private final TransactionTemplate txTemplate;
    private final String bucket;
    private final Duration staleAfter;
    private final int batchSize;

    public StalePendingReconciler(
            DocumentRepository documentRepository,
            MinioClient minioClient,
            PlatformTransactionManager txManager,
            @Value("${app.minio.bucket}") String bucket,
            @Value("${app.reconciler.pending-timeout-ms:3600000}") long staleAfterMs,
            @Value("${app.reconciler.batch-size:500}") int batchSize) {
        this.documentRepository = documentRepository;
        this.minioClient = minioClient;
        this.txTemplate = new TransactionTemplate(txManager);
        this.bucket = bucket;
        this.staleAfter = Duration.ofMillis(staleAfterMs);
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.reconciler.poll-interval-ms:900000}")
    public void cleanupStalePending() {
        Instant cutoff = Instant.now().minus(staleAfter);
        List<StaleDocument> stale =
                txTemplate.execute(
                        status ->
                                documentRepository
                                        .findByStatusAndStoragePathIsNullAndCreatedAtBefore(
                                                DocumentStatus.PENDING,
                                                cutoff,
                                                PageRequest.of(0, batchSize))
                                        .stream()
                                        .map(
                                                doc ->
                                                        new StaleDocument(
                                                                doc.getId(),
                                                                DocumentStorage.objectKey(
                                                                        doc.getUser().getId(),
                                                                        doc.getId(),
                                                                        doc.getOriginalFilename()),
                                                                doc.getCreatedAt()))
                                        .toList());
        if (stale == null) {
            return;
        }
        for (StaleDocument document : stale) {
            // MinIO removal runs outside any transaction so slow or unreachable storage never
            // pins a DB connection while the batch is being cleaned up.
            deleteOrphanedObject(document.storagePath());
            txTemplate.executeWithoutResult(
                    status ->
                            documentRepository.deleteByIdIfStale(
                                    document.id(), DocumentStatus.PENDING));
            log.warn(
                    "Reconciled stale PENDING document {} (no storage path, created {})",
                    document.id(),
                    document.createdAt());
        }
    }

    private void deleteOrphanedObject(String storagePath) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(storagePath).build());
        } catch (Exception e) {
            log.debug("No orphaned MinIO object to remove at {} (ignored)", storagePath, e);
        }
    }

    private record StaleDocument(Long id, String storagePath, Instant createdAt) {}
}
