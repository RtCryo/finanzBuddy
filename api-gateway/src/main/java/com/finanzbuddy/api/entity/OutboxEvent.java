package com.finanzbuddy.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(nullable = false, length = 255)
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "dead_lettered_at")
    private Instant deadLetteredAt;

    @Column(nullable = false)
    private int attempts;

    protected OutboxEvent() {}

    public OutboxEvent(Long aggregateId, String topic, String payload) {
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.payload = payload;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public void recordFailedAttempt() {
        this.attempts++;
    }

    public void deadLetter() {
        this.deadLetteredAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getAggregateId() {
        return aggregateId;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getDeadLetteredAt() {
        return deadLetteredAt;
    }

    public int getAttempts() {
        return attempts;
    }
}
