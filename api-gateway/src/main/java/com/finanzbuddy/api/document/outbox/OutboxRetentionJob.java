package com.finanzbuddy.api.document.outbox;

import com.finanzbuddy.api.repository.OutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purges successfully published outbox rows past their retention window so the table does not grow
 * without bound. Dead-lettered rows are intentionally kept for inspection.
 */
@Component
public class OutboxRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxRetentionJob.class);

    private final OutboxEventRepository outboxEventRepository;
    private final Duration retention;

    public OutboxRetentionJob(
            OutboxEventRepository outboxEventRepository,
            @Value("${app.outbox.retention-ms:604800000}") long retentionMs) {
        this.outboxEventRepository = outboxEventRepository;
        this.retention = Duration.ofMillis(retentionMs);
    }

    @Scheduled(fixedDelayString = "${app.outbox.purge-interval-ms:86400000}")
    @Transactional
    public void purgePublished() {
        Instant cutoff = Instant.now().minus(retention);
        int removed = outboxEventRepository.deletePublishedBefore(cutoff);
        if (removed > 0) {
            log.info("Purged {} published outbox events older than {}", removed, cutoff);
        }
    }
}
