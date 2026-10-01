package com.finanzbuddy.parser.processing.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedDocumentRepository extends JpaRepository<ProcessedDocument, Long> {}
