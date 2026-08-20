package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.HsnGstRate;
import com.builddash.backend.infra.persistence.entity.HsnGstRateEntity;

public final class HsnGstRateMapper {

    private HsnGstRateMapper() {
    }

    public static HsnGstRate toDomain(HsnGstRateEntity entity) {
        return new HsnGstRate(
                entity.getHsnCode(),
                entity.getDescription(),
                entity.getGstRatePercent(),
                entity.getCategory(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
