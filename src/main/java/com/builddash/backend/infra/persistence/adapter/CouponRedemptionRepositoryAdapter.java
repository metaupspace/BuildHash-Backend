package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.port.CouponRedemptionRepository;
import com.builddash.backend.infra.persistence.repository.CouponRedemptionJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
class CouponRedemptionRepositoryAdapter implements CouponRedemptionRepository {

    private final CouponRedemptionJpaRepository jpaRepository;

    CouponRedemptionRepositoryAdapter(CouponRedemptionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public int countByUserAndCoupon(UUID userId, UUID couponId) {
        return jpaRepository.countByUserIdAndCouponId(userId, couponId);
    }
}
