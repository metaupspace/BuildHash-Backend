package com.builddash.backend.infra.persistence.adapter;

import org.springframework.transaction.annotation.Transactional;

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

    @Override
    public java.util.List<com.builddash.backend.domain.model.CouponRedemption> findAllByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream()
                .map(e -> new com.builddash.backend.domain.model.CouponRedemption(
                        e.getId(), e.getCouponId(), e.getUserId(), e.getOrderId(), e.getRedeemedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void deleteByUserId(UUID userId) {
        jpaRepository.deleteByUserId(userId);
    }
}
