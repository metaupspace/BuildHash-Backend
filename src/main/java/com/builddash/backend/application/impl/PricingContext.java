package com.builddash.backend.application.impl;

import com.builddash.backend.domain.model.BulkPricingTier;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.ContractPrice;
import com.builddash.backend.domain.model.Coupon;
import com.builddash.backend.domain.model.MarginRule;
import com.builddash.backend.domain.model.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Everything PricingSteps needs, resolved once by PricingCalculatorImpl.loadContext before
 * the pipeline runs. Steps read this and never call a repository/port themselves.
 */
record PricingContext(
        Product product,
        Category category,
        List<BulkPricingTier> bulkPricingTiers,
        ContractPrice activeContractPrice,
        String requestedCouponCode,
        Coupon coupon,
        int couponRedemptionCountForUser,
        MarginRule marginRule,
        BigDecimal gstRatePercent,
        Instant asOf
) {
}
