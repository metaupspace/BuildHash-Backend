package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.enums.NotificationStatus;
import com.builddash.backend.domain.model.NotificationLog;
import com.builddash.backend.domain.port.NotificationLogRepository;
import com.builddash.backend.infra.persistence.mapper.NotificationLogMapper;
import com.builddash.backend.infra.persistence.repository.NotificationLogJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class NotificationLogRepositoryAdapter implements NotificationLogRepository {

    private final NotificationLogJpaRepository jpaRepository;


    @Override
    public NotificationLog save(NotificationLog log) {
        return NotificationLogMapper.toDomain(jpaRepository.save(NotificationLogMapper.toEntity(log)));
    }

    @Override
    public java.util.Optional<NotificationLog> findById(UUID id) {
        return jpaRepository.findById(id).map(NotificationLogMapper::toDomain);
    }

    @Override
    public boolean existsByEventTypeAndReferenceId(NotificationEventType eventType, UUID referenceId) {
        return jpaRepository.existsByEventTypeAndReferenceId(eventType, referenceId);
    }

    @Override
    public boolean existsByEventTypeAndReferenceIdAndCreatedAtAfter(NotificationEventType eventType, UUID referenceId, Instant cutoff) {
        return jpaRepository.existsByEventTypeAndReferenceIdAndCreatedAtAfter(eventType, referenceId, cutoff);
    }

    @Override
    public List<NotificationLog> findStalePending(Instant cutoff) {
        return jpaRepository.findByStatusAndCreatedAtBefore(NotificationStatus.PENDING, cutoff).stream()
                .map(NotificationLogMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void markSent(UUID id) {
        jpaRepository.markSent(id);
    }

    @Override
    @Transactional
    public void markFailed(UUID id) {
        jpaRepository.markFailed(id);
    }

    @Override
    public java.util.List<NotificationLog> findAllByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream()
                .map(NotificationLogMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteByUserId(UUID userId) {
        jpaRepository.deleteByUserId(userId);
    }
}
