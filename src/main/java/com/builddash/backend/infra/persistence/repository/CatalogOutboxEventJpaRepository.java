package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.domain.enums.OutboxStatus;
import com.builddash.backend.infra.persistence.entity.CatalogOutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CatalogOutboxEventJpaRepository extends JpaRepository<CatalogOutboxEventEntity, UUID> {

    List<CatalogOutboxEventEntity> findByStatus(OutboxStatus status);

    @Query("SELECT e FROM CatalogOutboxEventEntity e WHERE e.status = :status AND e.attemptCount < :maxAttempts ORDER BY e.createdAt ASC")
    List<CatalogOutboxEventEntity> findPendingForRelay(
            @Param("status") OutboxStatus status,
            @Param("maxAttempts") int maxAttempts,
            Pageable pageable
    );

    @Modifying
    @Query("update CatalogOutboxEventEntity e set e.status = com.builddash.backend.domain.enums.OutboxStatus.PUBLISHED where e.id = :id")
    void markPublished(@Param("id") UUID id);

    @Modifying
    @Query("update CatalogOutboxEventEntity e set e.status = com.builddash.backend.domain.enums.OutboxStatus.PROCESSED where e.id = :id")
    void markProcessed(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE CatalogOutboxEventEntity e SET e.attemptCount = :attemptCount, e.lastAttemptAt = :lastAttemptAt, e.errorMessage = :errorMessage, e.status = :status WHERE e.id = :id")
    void recordAttempt(
            @Param("id") UUID id,
            @Param("attemptCount") int attemptCount,
            @Param("lastAttemptAt") Instant lastAttemptAt,
            @Param("errorMessage") String errorMessage,
            @Param("status") OutboxStatus status
    );
}
