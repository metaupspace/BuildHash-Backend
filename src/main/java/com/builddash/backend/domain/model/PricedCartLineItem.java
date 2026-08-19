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
        String appliedItemCoupon
) {
}
