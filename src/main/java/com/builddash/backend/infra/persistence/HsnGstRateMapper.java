package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.model.HsnGstRate;

final class HsnGstRateMapper {

    private HsnGstRateMapper() {
    }

    static HsnGstRate toDomain(HsnGstRateEntity entity) {
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
