package com.finanzbuddy.api.document.adapter.out.persistence;

import com.finanzbuddy.api.document.domain.model.DocumentStatus;
import java.time.Instant;
import java.util.Collection;
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
public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {

    Page<DocumentEntity> findByUserId(Long userId, Pageable pageable);

    Page<DocumentEntity> findByUserIdAndOriginalFilenameContainingIgnoreCase(
            Long userId, String originalFilename, Pageable pageable);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, DocumentStatus status);

    long countByUserIdAndStatusIn(Long userId, Collection<DocumentStatus> statuses);

    Optional<DocumentEntity> findByIdAndUserId(Long id, Long userId);

    List<DocumentEntity> findByStatusAndStoragePathIsNullAndCreatedAtBefore(
            DocumentStatus status, Instant cutoff, Pageable pageable);

    /**
     * Deletes a document only if it is still stale (same status, still no storage path), so an
     * upload that finalizes between the reconciler's scan and its delete is not removed.
     */
    @Modifying
    @Query(
            "delete from DocumentEntity d"
                    + " where d.id = :id and d.status = :status and d.storagePath is null")
    int deleteByIdIfStale(@Param("id") Long id, @Param("status") DocumentStatus status);
}
