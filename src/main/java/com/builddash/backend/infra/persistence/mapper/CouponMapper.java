package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.Coupon;
import com.builddash.backend.infra.persistence.entity.CouponEntity;

public final class CouponMapper {

    private CouponMapper() {
    }

    public static Coupon toDomain(CouponEntity entity) {
        if (entity == null) return null;
        return new Coupon(
                entity.getId(),
                entity.getCode(),
                entity.getDiscountType(),
                entity.getDiscountValue(),
                entity.getMinOrderValue(),
                entity.getExpiresAt(),
                entity.getMaxUsesPerUser(),
                entity.getEligibleCategoryIds(),
                entity.isStackable(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
