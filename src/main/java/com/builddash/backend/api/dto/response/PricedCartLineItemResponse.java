package com.builddash.backend.api.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record PricedCartLineItemResponse(
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
