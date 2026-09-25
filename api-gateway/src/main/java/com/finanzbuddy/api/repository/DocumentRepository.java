package com.finanzbuddy.api.repository;

import com.finanzbuddy.api.entity.Document;
import com.finanzbuddy.api.entity.DocumentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Page<Document> findByUserId(Long userId, Pageable pageable);

    Optional<Document> findByIdAndUserId(Long id, Long userId);

    List<Document> findByStatusAndStoragePathIsNullAndCreatedAtBefore(
            DocumentStatus status, Instant cutoff);

    /**
     * Deletes a document only if it is still stale (same status, still no storage path), so an
     * upload that finalizes between the reconciler's scan and its delete is not removed.
     */
    @Modifying
    @Query(
            "delete from Document d"
                    + " where d.id = :id and d.status = :status and d.storagePath is null")
    int deleteByIdIfStale(@Param("id") Long id, @Param("status") DocumentStatus status);
}
