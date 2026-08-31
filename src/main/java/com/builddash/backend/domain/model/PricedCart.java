package com.builddash.backend.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * companyId flows from the source Cart so OrderServiceImpl can stamp orders.company_id
 * without re-reading the cart. Null for every B2C priced cart.
 */
public record PricedCart(
        UUID id,
        UUID userId,
        UUID projectId,
        List<PricedCartLineItem> items,
        BigDecimal subtotal,
        BigDecimal itemDiscountsTotal,
        BigDecimal cartDiscountTotal,
        BigDecimal totalGst,
        BigDecimal finalTotal,
        String appliedCartCoupon,
        String couponDroppedReason,
        UUID companyId
) {

    /** Compatibility constructor preserving the pre-9A call shape (companyId = null). */
    public PricedCart(UUID id, UUID userId, UUID projectId, List<PricedCartLineItem> items,
                      BigDecimal subtotal, BigDecimal itemDiscountsTotal, BigDecimal cartDiscountTotal,
                      BigDecimal totalGst, BigDecimal finalTotal, String appliedCartCoupon,
                      String couponDroppedReason) {
        this(id, userId, projectId, items, subtotal, itemDiscountsTotal, cartDiscountTotal,
                totalGst, finalTotal, appliedCartCoupon, couponDroppedReason, null);
    }
}
