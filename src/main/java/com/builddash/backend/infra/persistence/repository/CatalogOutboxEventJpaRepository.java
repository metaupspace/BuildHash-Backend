package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.domain.enums.OutboxStatus;
import com.builddash.backend.infra.persistence.entity.CatalogOutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CatalogOutboxEventJpaRepository extends JpaRepository<CatalogOutboxEventEntity, UUID> {

    List<CatalogOutboxEventEntity> findByStatus(OutboxStatus status);

    @Modifying
    @Query("update CatalogOutboxEventEntity e set e.status = com.builddash.backend.domain.enums.OutboxStatus.PUBLISHED where e.id = :id")
    void markPublished(UUID id);

    @Modifying
    @Query("update CatalogOutboxEventEntity e set e.status = com.builddash.backend.domain.enums.OutboxStatus.PROCESSED where e.id = :id")
    void markProcessed(UUID id);
}
