package com.builddash.backend.domain.port;

import java.util.UUID;

/**
 * Read-only in Phase 2 (see PLAN_PHASE2.md Section 5) — eligibility checks only.
 * No save(): redemption commit is deferred to whichever phase introduces Order.
 */
public interface CouponRedemptionRepository {

    int countByUserAndCoupon(UUID userId, UUID couponId);
}
