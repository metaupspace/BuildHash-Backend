package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.OutboxStatus;
import com.builddash.backend.domain.model.CatalogOutboxEvent;
import com.builddash.backend.domain.port.CatalogOutboxEventRepository;
import com.builddash.backend.infra.persistence.mapper.CatalogOutboxEventMapper;
import com.builddash.backend.infra.persistence.repository.CatalogOutboxEventJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class CatalogOutboxEventRepositoryAdapter implements CatalogOutboxEventRepository {

    private final CatalogOutboxEventJpaRepository jpaRepository;


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

    @Override
    @Transactional
    public void markProcessed(UUID id) {
        jpaRepository.markProcessed(id);
    }
}
