package com.builddash.backend.application.impl;

import com.builddash.backend.domain.model.BulkPricingTier;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Coupon;
import com.builddash.backend.domain.model.MarginRule;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.ResolvedContract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Everything PricingSteps needs, resolved once by PricingCalculatorImpl.loadContext before
 * the pipeline runs. Steps read this and never call a repository/port themselves.
 *
 * activeContract is the single contract-override WINNER (company tier beats user tier —
 * resolution happens in loadContext, decision 7), exposed as a ResolvedContract so the
 * pure steps carry no knowledge of contract tiers or persistence.
 *
 * unitPriceOverride is set when negotiated commercial pricing (e.g. converted RFQ quote)
 * overrides catalog base pricing.
 */
public record PricingContext(
        Product product,
        Category category,
        List<BulkPricingTier> bulkPricingTiers,
        ResolvedContract activeContract,
        String requestedCouponCode,
        Coupon coupon,
        int couponRedemptionCountForUser,
        MarginRule marginRule,
        BigDecimal gstRatePercent,
        Instant asOf,
        BigDecimal unitPriceOverride
) {
    public PricingContext(
            Product product,
            Category category,
            List<BulkPricingTier> bulkPricingTiers,
            ResolvedContract activeContract,
            String requestedCouponCode,
            Coupon coupon,
            int couponRedemptionCountForUser,
            MarginRule marginRule,
            BigDecimal gstRatePercent,
            Instant asOf
    ) {
        this(product, category, bulkPricingTiers, activeContract, requestedCouponCode,
                coupon, couponRedemptionCountForUser, marginRule, gstRatePercent, asOf, null);
    }
}
