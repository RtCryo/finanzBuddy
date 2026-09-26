package com.finanzbuddy.api.document.outbox;

import com.finanzbuddy.api.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Exposes the outbox's health signals: how deep the unpublished backlog is, how old its oldest
 * entry is (early warning for a stalled relay or a down consumer), and how many events have been
 * dead-lettered (silent data loss unless alerted on).
 *
 * <p>The backlog gauges read cached values refreshed on a fixed interval, so frequent or multiple
 * Prometheus scrapers do not each trigger a {@code COUNT}/{@code MIN} query against the database.
 */
@Component
public class OutboxMetrics {

    private final OutboxEventRepository outboxEventRepository;
    private final Counter deadLetteredCounter;
    private final AtomicLong pendingCount = new AtomicLong();
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong();

    public OutboxMetrics(OutboxEventRepository outboxEventRepository, MeterRegistry registry) {
        this.outboxEventRepository = outboxEventRepository;
        this.deadLetteredCounter =
                Counter.builder("outbox.events.dead_lettered")
                        .description("Outbox events dead-lettered after exhausting retries")
                        .register(registry);
        Gauge.builder("outbox.events.pending", pendingCount, AtomicLong::get)
                .description("Outbox events awaiting delivery")
                .register(registry);
        Gauge.builder("outbox.events.oldest_pending_age", oldestPendingAgeSeconds, AtomicLong::get)
                .description("Age of the oldest undelivered outbox event")
                .baseUnit("seconds")
                .register(registry);
    }

    public void recordDeadLettered() {
        deadLetteredCounter.increment();
    }

    @Scheduled(fixedDelayString = "${app.outbox.metrics-refresh-ms:15000}")
    public void refreshBacklogGauges() {
        pendingCount.set(outboxEventRepository.countByPublishedAtIsNullAndDeadLetteredAtIsNull());
        long ageSeconds =
                outboxEventRepository
                        .findOldestPendingCreatedAt()
                        .map(created -> Duration.between(created, Instant.now()).toSeconds())
                        .orElse(0L);
        oldestPendingAgeSeconds.set(ageSeconds);
    }
}
