package com.builddash.backend.api.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PricedCartResponse(
        UUID id,
        UUID userId,
        UUID projectId,
        List<PricedCartLineItemResponse> items,
        BigDecimal subtotal,
        BigDecimal itemDiscountsTotal,
        BigDecimal cartDiscountTotal,
        BigDecimal totalGst,
        BigDecimal finalTotal,
        String appliedCartCoupon,
        String couponDroppedReason
) {
}
