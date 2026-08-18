package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.BulkPricingTier;
import com.builddash.backend.domain.port.BulkPricingTierRepository;
import com.builddash.backend.infra.persistence.mapper.BulkPricingTierMapper;
import com.builddash.backend.infra.persistence.repository.BulkPricingTierJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class BulkPricingTierRepositoryAdapter implements BulkPricingTierRepository {

    private final BulkPricingTierJpaRepository jpaRepository;

    BulkPricingTierRepositoryAdapter(BulkPricingTierJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<BulkPricingTier> findByProductId(UUID productId) {
        return jpaRepository.findByProductId(productId).stream()
                .map(BulkPricingTierMapper::toDomain)
                .toList();
    }
}
