package com.finanzbuddy.api.document.adapter.out.messaging;

import com.finanzbuddy.api.document.adapter.out.persistence.OutboxEvent;
import com.finanzbuddy.api.document.adapter.out.persistence.OutboxEventRepository;
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
 * Purges successfully published outbox rows past their retention window so the table does not grow
 * without bound. Dead-lettered rows are intentionally kept for inspection. Deletes are performed in
 * small batches to avoid long-lived locks on the outbox table.
 */
@Component
public class OutboxRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxRetentionJob.class);

    private final OutboxEventRepository outboxEventRepository;
    private final TransactionTemplate txTemplate;
    private final Duration retention;
    private final int batchSize;

    public OutboxRetentionJob(
            OutboxEventRepository outboxEventRepository,
            PlatformTransactionManager txManager,
            @Value("${app.outbox.retention-ms:604800000}") long retentionMs,
            @Value("${app.outbox.purge-batch-size:1000}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.txTemplate = new TransactionTemplate(txManager);
        this.retention = Duration.ofMillis(retentionMs);
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.outbox.purge-interval-ms:86400000}")
    public void purgePublished() {
        Instant cutoff = Instant.now().minus(retention);
        int totalRemoved = 0;
        int removedInBatch;
        do {
            // Each batch runs in its own short transaction so locks and the DB connection are
            // released between batches instead of being held for the whole purge run.
            removedInBatch = purgeBatch(cutoff);
            totalRemoved += removedInBatch;
        } while (removedInBatch == batchSize);

        if (totalRemoved > 0) {
            log.info("Purged {} published outbox events older than {}", totalRemoved, cutoff);
        }
    }

    private int purgeBatch(Instant cutoff) {
        Integer removed =
                txTemplate.execute(
                        status -> {
                            List<OutboxEvent> batch =
                                    outboxEventRepository.findByPublishedAtBefore(
                                            cutoff, PageRequest.of(0, batchSize));
                            if (!batch.isEmpty()) {
                                outboxEventRepository.deleteAllInBatch(batch);
                            }
                            return batch.size();
                        });
        return removed == null ? 0 : removed;
    }
}
