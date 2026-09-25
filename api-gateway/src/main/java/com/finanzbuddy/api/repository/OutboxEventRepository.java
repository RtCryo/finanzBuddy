package com.finanzbuddy.api.repository;

import com.finanzbuddy.api.entity.OutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByPublishedAtIsNullAndDeadLetteredAtIsNullOrderByCreatedAtAsc(
            Pageable pageable);

    long countByPublishedAtIsNullAndDeadLetteredAtIsNull();

    @Query(
            "select min(o.createdAt) from OutboxEvent o"
                    + " where o.publishedAt is null and o.deadLetteredAt is null")
    Optional<Instant> findOldestPendingCreatedAt();
}
