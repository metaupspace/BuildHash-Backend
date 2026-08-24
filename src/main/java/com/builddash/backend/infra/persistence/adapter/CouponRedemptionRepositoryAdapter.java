package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.port.CouponRedemptionRepository;
import com.builddash.backend.infra.persistence.repository.CouponRedemptionJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class CouponRedemptionRepositoryAdapter implements CouponRedemptionRepository {

    private final CouponRedemptionJpaRepository jpaRepository;


    @Override
    public int countByUserAndCoupon(UUID userId, UUID couponId) {
        return jpaRepository.countByUserIdAndCouponId(userId, couponId);
    }

    @Override
    public void record(UUID userId, UUID couponId, UUID orderId) {
        com.builddash.backend.infra.persistence.entity.CouponRedemptionEntity entity =
                new com.builddash.backend.infra.persistence.entity.CouponRedemptionEntity();
        entity.setCouponId(couponId);
        entity.setUserId(userId);
        entity.setOrderId(orderId);
        entity.setRedeemedAt(java.time.Instant.now());
        jpaRepository.save(entity);
    }
}
