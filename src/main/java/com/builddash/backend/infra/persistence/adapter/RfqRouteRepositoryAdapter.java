package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.port.RfqRouteRepository;
import com.builddash.backend.infra.persistence.entity.RfqRouteEntity;
import com.builddash.backend.infra.persistence.repository.RfqRouteJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class RfqRouteRepositoryAdapter implements RfqRouteRepository {

    private final RfqRouteJpaRepository jpaRepository;

    @Override
    public void saveAll(UUID rfqId, Collection<UUID> vendorIds) {
        jpaRepository.saveAll(vendorIds.stream()
                .map(vendorId -> new RfqRouteEntity(rfqId, vendorId))
                .toList());
    }

    @Override
    public List<UUID> findVendorIdsByRfqId(UUID rfqId) {
        return jpaRepository.findByRfqId(rfqId).stream()
                .map(RfqRouteEntity::getVendorId)
                .toList();
    }

    @Override
    public boolean existsByRfqIdAndVendorId(UUID rfqId, UUID vendorId) {
        return jpaRepository.existsByRfqIdAndVendorId(rfqId, vendorId);
    }
}
