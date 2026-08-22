package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.SlotConfiguration;
import com.builddash.backend.domain.port.SlotConfigurationRepository;
import com.builddash.backend.infra.persistence.mapper.DeliverySlotMapper;
import com.builddash.backend.infra.persistence.repository.SlotConfigurationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class SlotConfigurationRepositoryAdapter implements SlotConfigurationRepository {

    private final SlotConfigurationJpaRepository jpaRepository;

    @Override
    public List<SlotConfiguration> findAllActive() {
        return jpaRepository.findByIsActiveTrue().stream()
                .map(DeliverySlotMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<SlotConfiguration> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(DeliverySlotMapper::toDomain);
    }
}
