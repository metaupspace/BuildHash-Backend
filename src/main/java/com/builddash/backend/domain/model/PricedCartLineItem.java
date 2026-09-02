package com.builddash.backend.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record PricedCartLineItem(
        UUID productId,
        int quantity,
        String hsnCode,
        BigDecimal unitBasePrice,
        BigDecimal unitFinalPrice,
        BigDecimal lineSubtotal,
        BigDecimal lineDiscount,
        BigDecimal lineGst,
        BigDecimal lineFinalTotal,
        String appliedItemCoupon,
        BigDecimal taxRatePercent,
        BigDecimal allocatedDiscount
) {
    /** Compatibility constructor preserving pre-H4 call shape. */
    public PricedCartLineItem(
            UUID productId,
            int quantity,
            String hsnCode,
            BigDecimal unitBasePrice,
            BigDecimal unitFinalPrice,
            BigDecimal lineSubtotal,
            BigDecimal lineDiscount,
            BigDecimal lineGst,
            BigDecimal lineFinalTotal,
            String appliedItemCoupon
    ) {
        this(productId, quantity, hsnCode, unitBasePrice, unitFinalPrice,
                lineSubtotal, lineDiscount, lineGst, lineFinalTotal, appliedItemCoupon, null, BigDecimal.ZERO);
    }
}
