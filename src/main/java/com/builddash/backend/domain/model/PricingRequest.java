package com.builddash.backend.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * companyId is null for every B2C request — the existing behavior is unchanged on that
 * path. When set (B2B carts), PricingCalculatorImpl.loadContext resolves the company
 * contract tier first (company -> user -> fallback precedence lives there, and only
 * there).
 *
 * unitPriceOverride is set for negotiated pricing (e.g. converted RFQ quotes) and
 * supersedes catalog base pricing.
 */
public record PricingRequest(
        UUID productId,
        int quantity,
        UUID userId,
        String couponCode,
        UUID companyId,
        BigDecimal unitPriceOverride
) {

    /** Compatibility constructor preserving the 5-arg call shape. */
    public PricingRequest(UUID productId, int quantity, UUID userId, String couponCode, UUID companyId) {
        this(productId, quantity, userId, couponCode, companyId, null);
    }

    /** Compatibility constructor preserving the Phase 2 call shape (companyId = null). */
    public PricingRequest(UUID productId, int quantity, UUID userId, String couponCode) {
        this(productId, quantity, userId, couponCode, null, null);
    }
}
