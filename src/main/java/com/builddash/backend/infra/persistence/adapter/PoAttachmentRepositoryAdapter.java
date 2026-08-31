package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.PoAttachmentStatus;
import com.builddash.backend.domain.model.PoAttachment;
import com.builddash.backend.domain.port.PoAttachmentRepository;
import com.builddash.backend.infra.persistence.entity.PoAttachmentEntity;
import com.builddash.backend.infra.persistence.repository.PoAttachmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 9-B lesson standard: domain results are built from the entity returned by
 * save/saveAndFlush — caller-supplied ids are never assumed authoritative.
 */
@Repository
@RequiredArgsConstructor
class PoAttachmentRepositoryAdapter implements PoAttachmentRepository {

    private final PoAttachmentJpaRepository jpaRepository;

    @Override
    @Transactional
    public PoAttachment save(PoAttachment attachment) {
        PoAttachmentEntity entity = jpaRepository.findById(attachment.id())
                .orElseGet(() -> {
                    PoAttachmentEntity e = new PoAttachmentEntity();
                    e.setId(attachment.id());
                    e.setStatus(attachment.status());
                    return e;
                });
        entity.setOrderId(attachment.orderId());
        entity.setStorageKey(attachment.storageKey());
        entity.setContentType(attachment.contentType());
        entity.setByteSize(attachment.byteSize());
        entity.setUploadedBy(attachment.uploadedBy());
        entity.setStatus(attachment.status());
        PoAttachmentEntity saved = jpaRepository.saveAndFlush(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PoAttachment> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional
    public Optional<PoAttachment> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PoAttachment> findByOrderId(UUID orderId) {
        return jpaRepository.findByOrderId(orderId).map(this::toDomain);
    }

    @Override
    @Transactional
    public Optional<PoAttachment> finalizeStored(UUID attachmentId, String contentType, int byteSize, UUID uploadedBy) {
        int updated = jpaRepository.finalizeStored(attachmentId,
                PoAttachmentStatus.PENDING, PoAttachmentStatus.STORED,
                contentType, byteSize, uploadedBy, Instant.now());
        if (updated == 0) {
            return Optional.empty(); // concurrent retry already finalized — caller re-reads
        }
        return jpaRepository.findById(attachmentId).map(this::toDomain);
    }

    private PoAttachment toDomain(PoAttachmentEntity e) {
        return new PoAttachment(e.getId(), e.getOrderId(), e.getStorageKey(), e.getContentType(),
                e.getByteSize(), e.getUploadedBy(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
