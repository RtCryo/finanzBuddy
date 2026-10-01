package com.finanzbuddy.api.cleanup.domain;

import com.finanzbuddy.api.cleanup.port.in.ReconcileStalePendingUseCase;
import com.finanzbuddy.api.cleanup.port.out.ClockPort;
import com.finanzbuddy.api.document.DocumentStorage;
import com.finanzbuddy.api.document.domain.model.Document;
import com.finanzbuddy.api.document.domain.model.DocumentStatus;
import com.finanzbuddy.api.document.domain.model.PageRequest;
import com.finanzbuddy.api.document.port.out.DocumentRepositoryPort;
import com.finanzbuddy.api.document.port.out.FileStoragePort;
import com.finanzbuddy.api.document.port.out.TransactionRunnerPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Removes documents left in {@code PENDING} with no storage path — the signature of an upload whose
 * metadata transaction never completed (e.g. the JVM died between persisting the row and storing
 * the file). The orphaned storage object, if it was written, is deleted best-effort using the
 * deterministic path.
 */
public final class StalePendingReconciliation implements ReconcileStalePendingUseCase {

    private static final Logger log = LoggerFactory.getLogger(StalePendingReconciliation.class);

    private final DocumentRepositoryPort documentRepository;
    private final FileStoragePort fileStorage;
    private final TransactionRunnerPort transactionRunner;
    private final ClockPort clock;
    private final Duration staleAfter;
    private final int batchSize;

    public StalePendingReconciliation(
            DocumentRepositoryPort documentRepository,
            FileStoragePort fileStorage,
            TransactionRunnerPort transactionRunner,
            ClockPort clock,
            Duration staleAfter,
            int batchSize) {
        this.documentRepository = documentRepository;
        this.fileStorage = fileStorage;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
        this.staleAfter = staleAfter;
        this.batchSize = batchSize;
    }

    @Override
    public void reconcile() {
        Instant cutoff = clock.now().minus(staleAfter);
        List<Document> stale =
                transactionRunner.inTransaction(
                        () ->
                                documentRepository.findStalePending(
                                        DocumentStatus.PENDING,
                                        cutoff,
                                        new PageRequest(0, batchSize, null)));
        for (Document document : stale) {
            // Delete the row first, conditionally on it still being stale; only then remove the
            // storage object. This avoids wiping the object of an upload that finalized
            // (storage_path set) between the scan and now. Object I/O still runs outside any
            // transaction so slow or unreachable storage never pins a DB connection.
            int deleted =
                    transactionRunner.inTransaction(
                            () ->
                                    documentRepository.deleteByIdIfStale(
                                            document.id(), DocumentStatus.PENDING));
            if (deleted > 0) {
                removeOrphanedObject(
                        DocumentStorage.objectKey(
                                document.ownerId(),
                                document.id(),
                                document.originalFilename()));
                log.warn(
                        "Reconciled stale PENDING document {} (no storage path, created {})",
                        document.id(),
                        document.createdAt());
            }
        }
    }

    private void removeOrphanedObject(String storagePath) {
        try {
            fileStorage.remove(storagePath);
        } catch (RuntimeException e) {
            log.debug("No orphaned object to remove at {} (ignored)", storagePath, e);
        }
    }
}
