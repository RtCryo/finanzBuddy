package com.finanzbuddy.parser.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "processed_documents")
public class ProcessedDocument {

    @Id
    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    @Column(name = "saved_count")
    private Integer savedCount;

    @Column(name = "skipped_count")
    private Integer skippedCount;

    protected ProcessedDocument() {}

    public ProcessedDocument(Long documentId) {
        this.documentId = documentId;
    }

    @PrePersist
    protected void onCreate() {
        processedAt = Instant.now();
    }

    public Integer getSavedCount() {
        return savedCount;
    }

    public Integer getSkippedCount() {
        return skippedCount;
    }

    public void recordOutcome(int saved, int skipped) {
        this.savedCount = saved;
        this.skippedCount = skipped;
    }
}
