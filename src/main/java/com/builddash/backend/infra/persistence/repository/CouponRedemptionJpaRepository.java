package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.CouponRedemptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CouponRedemptionJpaRepository extends JpaRepository<CouponRedemptionEntity, UUID> {

    int countByUserIdAndCouponId(UUID userId, UUID couponId);
}
