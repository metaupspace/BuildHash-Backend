package com.builddash.backend.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Immutable result of the pricing pipeline (see application/impl/PricingCalculatorImpl,
 * Phase 2). Every field is set once by the step that populates it — this is a computed
 * value flowing through a pure pipeline, not persisted entity state, so it's a record
 * rather than the mutable-POJO style used for HsnGstRate/Product.
 */
public record PriceCalculationResult(
        UUID productId,
        int quantity,
        String hsnCode,

        BigDecimal basePrice,
        BigDecimal basePriceTotal,

        UUID appliedTierId,
        BigDecimal tierUnitPrice,
        BigDecimal tierAdjustedTotal,

        UUID appliedContractId,
        BigDecimal contractUnitPrice,
        BigDecimal contractAdjustedTotal,

        UUID appliedCouponId,
        BigDecimal couponDiscountAmount,

        BigDecimal preFloorPrice,
        boolean marginFloorTriggered,
        BigDecimal marginFloorAdjustment,
        UUID appliedMarginRuleId,

        BigDecimal subtotal,

        BigDecimal gstRatePercent,
        BigDecimal gstAmount,

        BigDecimal finalPrice
) {

    public static PriceCalculationResult initial(PricingRequest request, String hsnCode, BigDecimal basePrice) {
        return new PriceCalculationResult(
                request.productId(),
                request.quantity(),
                hsnCode,
                basePrice,
                basePrice.multiply(BigDecimal.valueOf(request.quantity())),
                null, null, null,
                null, null, null,
                null, BigDecimal.ZERO,
                null, false, BigDecimal.ZERO, null,
                null,
                null, null,
                null
        );
    }
}
