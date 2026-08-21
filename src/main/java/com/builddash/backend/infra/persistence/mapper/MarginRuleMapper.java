package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.MarginRule;
import com.builddash.backend.infra.persistence.entity.MarginRuleEntity;

public final class MarginRuleMapper {

    private MarginRuleMapper() {
    }

    public static MarginRule toDomain(MarginRuleEntity entity) {
        return new MarginRule(
                entity.getId(),
                entity.getProductId(),
                entity.getCategoryId(),
                entity.getCostPrice(),
                entity.getFloorPercent(),
                entity.getFloorPrice(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
