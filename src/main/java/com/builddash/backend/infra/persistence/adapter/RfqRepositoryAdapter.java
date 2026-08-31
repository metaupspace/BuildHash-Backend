package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.RfqStatus;
import com.builddash.backend.domain.model.Rfq;
import com.builddash.backend.domain.model.RfqItem;
import com.builddash.backend.domain.port.RfqItemRepository;
import com.builddash.backend.domain.port.RfqRepository;
import com.builddash.backend.domain.port.RfqRouteRepository;
import com.builddash.backend.infra.persistence.entity.RfqEntity;
import com.builddash.backend.infra.persistence.repository.RfqJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Header persistence only: items and routes are written once at creation by
 * RfqServiceImpl (same transaction) and are immutable afterwards — a status
 * transition must never re-insert or recalculate routing.
 */
@Repository
@RequiredArgsConstructor
class RfqRepositoryAdapter implements RfqRepository {

    private final RfqJpaRepository jpaRepository;
    private final RfqItemRepository rfqItemRepository;
    private final RfqRouteRepository rfqRouteRepository;

    @Override
    @Transactional
    public Rfq save(Rfq rfq) {
        RfqEntity entity = jpaRepository.findById(rfq.id())
                .orElseGet(() -> {
                    RfqEntity e = new RfqEntity();
                    e.setId(rfq.id());
                    return e;
                });
        entity.setCompanyId(rfq.companyId());
        entity.setCreatedByUserId(rfq.createdByUserId());
        entity.setStatus(rfq.status());
        entity.setExpiresAt(rfq.expiresAt());
        entity.setNotes(rfq.notes());
        RfqEntity saved = jpaRepository.saveAndFlush(entity);
        return toDomain(saved, rfq.items(), rfq.routedVendorIds());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Rfq> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomainLoaded);
    }

    @Override
    @Transactional
    public Optional<Rfq> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(this::toDomainLoaded);
    }

    @Override
    @Transactional
    public int expireOpenBefore(Instant now) {
        return jpaRepository.expireOpenBefore(now, RfqStatus.OPEN, RfqStatus.EXPIRED);
    }

    private Rfq toDomainLoaded(RfqEntity entity) {
        List<RfqItem> items = rfqItemRepository.findByRfqId(entity.getId());
        List<UUID> routes = rfqRouteRepository.findVendorIdsByRfqId(entity.getId());
        return toDomain(entity, items, routes);
    }

    private Rfq toDomain(RfqEntity entity, List<RfqItem> items, List<UUID> routedVendorIds) {
        return new Rfq(entity.getId(), entity.getCompanyId(), entity.getCreatedByUserId(),
                entity.getStatus(), entity.getExpiresAt(), entity.getNotes(),
                items, routedVendorIds, entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
