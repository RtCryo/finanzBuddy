package com.finanzbuddy.api.document.cleanup;

import com.finanzbuddy.api.entity.Document;
import com.finanzbuddy.api.entity.DocumentStatus;
import com.finanzbuddy.api.repository.DocumentRepository;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes documents left in {@code PENDING} with no storage path — the signature of an upload whose
 * metadata transaction never completed (e.g. the JVM died between persisting the row and storing the
 * file). The MinIO object, if it was written, is deleted best-effort using the deterministic path.
 */
@Component
public class StalePendingReconciler {

    private static final Logger log = LoggerFactory.getLogger(StalePendingReconciler.class);

    private final DocumentRepository documentRepository;
    private final MinioClient minioClient;
    private final String bucket;
    private final Duration staleAfter;

    public StalePendingReconciler(
            DocumentRepository documentRepository,
            MinioClient minioClient,
            @Value("${app.minio.bucket}") String bucket,
            @Value("${app.reconciler.pending-timeout-ms:3600000}") long staleAfterMs) {
        this.documentRepository = documentRepository;
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.staleAfter = Duration.ofMillis(staleAfterMs);
    }

    @Scheduled(fixedDelayString = "${app.reconciler.poll-interval-ms:900000}")
    @Transactional
    public void cleanupStalePending() {
        Instant cutoff = Instant.now().minus(staleAfter);
        List<Document> stale =
                documentRepository.findByStatusAndStoragePathIsNullAndCreatedAtBefore(
                        DocumentStatus.PENDING, cutoff);
        for (Document document : stale) {
            deleteOrphanedObject(document);
            documentRepository.delete(document);
            log.warn(
                    "Reconciled stale PENDING document {} (no storage path, created {})",
                    document.getId(),
                    document.getCreatedAt());
        }
    }

    private void deleteOrphanedObject(Document document) {
        String storagePath =
                String.format(
                        "users/%d/%d-%s",
                        document.getUser().getId(),
                        document.getId(),
                        Paths.get(document.getOriginalFilename()).getFileName().toString());
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(storagePath).build());
        } catch (Exception e) {
            log.debug("No orphaned MinIO object to remove at {} (ignored)", storagePath, e);
        }
    }
}
