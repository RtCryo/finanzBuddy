package com.finanzbuddy.parser.repository;

import com.finanzbuddy.parser.entity.ProcessedDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedDocumentRepository extends JpaRepository<ProcessedDocument, Long> {

    List<ProcessedDocument> findTop20ByUserIdAndStatusConfirmedFalseOrderByProcessedAtAsc(
            Long userId);

    @Modifying
    @Query("update ProcessedDocument d set d.statusConfirmed = true where d.documentId = :id")
    int markConfirmed(@Param("id") Long id);
}
