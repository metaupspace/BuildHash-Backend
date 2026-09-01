package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.enums.NotificationStatus;
import com.builddash.backend.infra.persistence.entity.NotificationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationLogJpaRepository extends JpaRepository<NotificationLogEntity, UUID> {

    boolean existsByEventTypeAndReferenceId(NotificationEventType eventType, UUID referenceId);

    boolean existsByEventTypeAndReferenceIdAndUserId(NotificationEventType eventType, UUID referenceId, UUID userId);

    boolean existsByEventTypeAndReferenceIdAndCreatedAtAfter(NotificationEventType eventType, UUID referenceId, Instant cutoff);

    List<NotificationLogEntity> findByStatusAndCreatedAtBefore(NotificationStatus status, Instant cutoff);

    @Modifying
    @Query("update NotificationLogEntity n set n.status = com.builddash.backend.domain.enums.NotificationStatus.SENT, n.sentAt = CURRENT_TIMESTAMP where n.id = :id")
    void markSent(UUID id);

    @Modifying
    @Query("update NotificationLogEntity n set n.status = com.builddash.backend.domain.enums.NotificationStatus.FAILED where n.id = :id")
    void markFailed(UUID id);

    java.util.List<NotificationLogEntity> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
