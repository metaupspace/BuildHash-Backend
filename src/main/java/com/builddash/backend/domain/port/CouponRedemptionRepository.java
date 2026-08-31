package com.builddash.backend.domain.port;

import java.util.UUID;

/**
 * Redemption reads for eligibility checks plus the write performed when an order
 * actually applies a coupon (Phase 3+).
 */
public interface CouponRedemptionRepository {

    int countByUserAndCoupon(UUID userId, UUID couponId);

    void record(UUID userId, UUID couponId, UUID orderId);

    /** DPDP export: every coupon the user redeemed, with order linkage. */
    java.util.List<com.builddash.backend.domain.model.CouponRedemption> findAllByUserId(UUID userId);

    /** DPDP hard-delete (PLAN_PHASE8 5(d)). */
    void deleteByUserId(UUID userId);
}
