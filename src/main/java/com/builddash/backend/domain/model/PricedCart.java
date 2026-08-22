package com.builddash.backend.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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
        String couponDroppedReason
) {
}
