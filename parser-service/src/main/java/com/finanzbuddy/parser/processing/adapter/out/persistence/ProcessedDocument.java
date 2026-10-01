package com.finanzbuddy.parser.processing.adapter.out.persistence;

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

    protected ProcessedDocument() {}

    public ProcessedDocument(Long documentId) {
        this.documentId = documentId;
    }

    @PrePersist
    protected void onCreate() {
        processedAt = Instant.now();
    }
}
