package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.enums.OutboxStatus;
import com.builddash.backend.domain.model.CatalogOutboxEvent;
import com.builddash.backend.domain.port.CatalogOutboxEventRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
class CatalogOutboxEventRepositoryAdapter implements CatalogOutboxEventRepository {

    private final CatalogOutboxEventJpaRepository jpaRepository;

    CatalogOutboxEventRepositoryAdapter(CatalogOutboxEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CatalogOutboxEvent save(CatalogOutboxEvent event) {
        return CatalogOutboxEventMapper.toDomain(jpaRepository.save(CatalogOutboxEventMapper.toEntity(event)));
    }

    @Override
    public List<CatalogOutboxEvent> findByStatus(OutboxStatus status) {
        return jpaRepository.findByStatus(status).stream()
                .map(CatalogOutboxEventMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void markPublished(UUID id) {
        jpaRepository.markPublished(id);
    }
}
