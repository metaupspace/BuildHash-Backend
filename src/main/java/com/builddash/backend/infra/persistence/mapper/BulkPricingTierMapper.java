package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.BulkPricingTier;
import com.builddash.backend.infra.persistence.entity.BulkPricingTierEntity;

public final class BulkPricingTierMapper {

    private BulkPricingTierMapper() {
    }

    public static BulkPricingTier toDomain(BulkPricingTierEntity entity) {
        return new BulkPricingTier(
                entity.getId(),
                entity.getProductId(),
                entity.getMinQuantity(),
                entity.getUnitPrice(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
