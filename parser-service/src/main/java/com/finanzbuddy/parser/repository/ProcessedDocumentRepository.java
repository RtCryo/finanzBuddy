package com.finanzbuddy.parser.repository;

import com.finanzbuddy.parser.entity.ProcessedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedDocumentRepository extends JpaRepository<ProcessedDocument, Long> {}
