package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.ContractPrice;
import com.builddash.backend.infra.persistence.entity.ContractPriceEntity;

public final class ContractPriceMapper {

    private ContractPriceMapper() {
    }

    public static ContractPrice toDomain(ContractPriceEntity entity) {
        return new ContractPrice(
                entity.getId(),
                entity.getUserId(),
                entity.getProductId(),
                entity.getUnitPrice(),
                entity.getEffectiveFrom(),
                entity.getEffectiveTo(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static ContractPriceEntity toEntity(ContractPrice contractPrice) {
        ContractPriceEntity entity = new ContractPriceEntity();
        entity.setId(contractPrice.getId());
        entity.setUserId(contractPrice.getUserId());
        entity.setProductId(contractPrice.getProductId());
        entity.setUnitPrice(contractPrice.getUnitPrice());
        entity.setEffectiveFrom(contractPrice.getEffectiveFrom());
        entity.setEffectiveTo(contractPrice.getEffectiveTo());
        entity.setCreatedAt(contractPrice.getCreatedAt());
        entity.setUpdatedAt(contractPrice.getUpdatedAt());
        return entity;
    }
}
