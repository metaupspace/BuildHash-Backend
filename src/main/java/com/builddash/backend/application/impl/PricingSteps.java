package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.DiscountType;
import com.builddash.backend.domain.exception.BadRequestException;
import com.builddash.backend.domain.exception.GstRateUnresolvedException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.BulkPricingTier;
import com.builddash.backend.domain.model.Coupon;
import com.builddash.backend.domain.model.MarginRule;
import com.builddash.backend.domain.model.PriceCalculationResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Pure pipeline steps: each takes the running result and the pre-resolved PricingContext,
 * returns the next result. No repository/port calls in here — all I/O happens once in
 * PricingCalculatorImpl.loadContext before the fold runs (see PLAN_PHASE2.md Section 3).
 */
final @Slf4j
class PricingSteps {

    private PricingSteps() {
    }

    static PriceCalculationResult applyBulkTier(PriceCalculationResult running, PricingContext ctx) {
        Optional<BulkPricingTier> matchedTier = ctx.bulkPricingTiers().stream()
                .filter(tier -> tier.getMinQuantity() <= running.quantity())
                .max(Comparator.comparingInt(BulkPricingTier::getMinQuantity));

        if (matchedTier.isEmpty()) {
            return copy(running).tierAdjustedTotal(running.basePriceTotal()).build();
        }

        BulkPricingTier tier = matchedTier.get();
        BigDecimal tierAdjustedTotal = tier.getUnitPrice().multiply(BigDecimal.valueOf(running.quantity()));
        return copy(running)
                .appliedTierId(tier.getId())
                .tierUnitPrice(tier.getUnitPrice())
                .tierAdjustedTotal(tierAdjustedTotal)
                .build();
    }

    static PriceCalculationResult applyContractOverride(PriceCalculationResult running, PricingContext ctx) {
        if (ctx.activeContractPrice() == null) {
            return copy(running).contractAdjustedTotal(running.tierAdjustedTotal()).build();
        }

        BigDecimal contractAdjustedTotal = ctx.activeContractPrice().getUnitPrice()
                .multiply(BigDecimal.valueOf(running.quantity()));
        return copy(running)
                .appliedContractId(ctx.activeContractPrice().getId())
                .contractUnitPrice(ctx.activeContractPrice().getUnitPrice())
                .contractAdjustedTotal(contractAdjustedTotal)
                .build();
    }

    static PriceCalculationResult applyCoupon(PriceCalculationResult running, PricingContext ctx) {
        if (ctx.requestedCouponCode() == null) {
            return copy(running).couponDiscountAmount(BigDecimal.ZERO).build();
        }

        Coupon coupon = ctx.coupon();
        if (coupon == null || !coupon.isActive()) {
            throw new NotFoundException("COUPON_NOT_FOUND", "Coupon not found: " + ctx.requestedCouponCode());
        }
        if (coupon.getExpiresAt().isBefore(ctx.asOf())) {
            throw new BadRequestException("COUPON_EXPIRED", "Coupon has expired: " + coupon.getCode());
        }
        if (coupon.getMaxUsesPerUser() != null && ctx.couponRedemptionCountForUser() >= coupon.getMaxUsesPerUser()) {
            throw new BadRequestException("COUPON_USAGE_LIMIT_REACHED",
                    "Coupon usage limit reached: " + coupon.getCode());
        }
        if (!coupon.getEligibleCategoryIds().isEmpty()
                && !coupon.getEligibleCategoryIds().contains(ctx.product().getCategoryId())) {
            throw new BadRequestException("COUPON_CATEGORY_INELIGIBLE",
                    "Coupon not eligible for this product's category: " + coupon.getCode());
        }

        BigDecimal rawDiscount = coupon.getDiscountType() == DiscountType.PERCENT
                ? running.contractAdjustedTotal().multiply(coupon.getDiscountValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : coupon.getDiscountValue();
        BigDecimal discount = rawDiscount.min(running.contractAdjustedTotal());

        return copy(running)
                .appliedCouponId(coupon.getId())
                .couponDiscountAmount(discount)
                .build();
    }

    static PriceCalculationResult applyMarginFloor(PriceCalculationResult running, PricingContext ctx) {
        BigDecimal preFloorPrice = running.contractAdjustedTotal().subtract(running.couponDiscountAmount());
        MarginRule rule = ctx.marginRule();

        if (rule == null) {
            return copy(running).preFloorPrice(preFloorPrice).subtotal(preFloorPrice).build();
        }

        BigDecimal floor = resolveFloor(rule);
        if (floor == null) {
            log.warn("Malformed margin rule {} for product {} has neither floorPrice nor a usable " +
                            "costPrice+floorPercent combination — pricing proceeds without a floor check",
                    rule.getId(), running.productId());
            return copy(running).preFloorPrice(preFloorPrice).subtotal(preFloorPrice).build();
        }

        boolean triggered = preFloorPrice.compareTo(floor) < 0;
        BigDecimal adjustment = triggered ? floor.subtract(preFloorPrice) : BigDecimal.ZERO;
        BigDecimal subtotal = preFloorPrice.add(adjustment);

        return copy(running)
                .preFloorPrice(preFloorPrice)
                .marginFloorTriggered(triggered)
                .marginFloorAdjustment(adjustment)
                .appliedMarginRuleId(rule.getId())
                .subtotal(subtotal)
                .build();
    }

    static PriceCalculationResult applyGst(PriceCalculationResult running, PricingContext ctx) {
        if (ctx.gstRatePercent() == null) {
            throw new GstRateUnresolvedException(running.hsnCode());
        }

        BigDecimal gstAmount = running.subtotal().multiply(ctx.gstRatePercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal finalPrice = running.subtotal().add(gstAmount);

        return copy(running)
                .gstRatePercent(ctx.gstRatePercent())
                .gstAmount(gstAmount)
                .finalPrice(finalPrice)
                .build();
    }

    private static Result copy(PriceCalculationResult running) {
        return new Result(running);
    }

    private static BigDecimal resolveFloor(MarginRule rule) {
        if (rule.getFloorPrice() != null) {
            return rule.getFloorPrice();
        }
        if (rule.getCostPrice() != null && rule.getFloorPercent() != null) {
            BigDecimal multiplier = BigDecimal.ONE.add(
                    rule.getFloorPercent().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            return rule.getCostPrice().multiply(multiplier);
        }
        return null;
    }

    /**
     * PriceCalculationResult has ~20 fields and each step only ever changes a handful of
     * them — this mutable builder seeded from the running record is just a local way to
     * write "copy running, change these fields" without a 20-argument record constructor
     * call at every step. It never crosses a step boundary as mutable state.
     */
    private static final class Result {
        private java.util.UUID productId;
        private int quantity;
        private String hsnCode;
        private BigDecimal basePrice;
        private BigDecimal basePriceTotal;
        private java.util.UUID appliedTierId;
        private BigDecimal tierUnitPrice;
        private BigDecimal tierAdjustedTotal;
        private java.util.UUID appliedContractId;
        private BigDecimal contractUnitPrice;
        private BigDecimal contractAdjustedTotal;
        private java.util.UUID appliedCouponId;
        private BigDecimal couponDiscountAmount;
        private BigDecimal preFloorPrice;
        private boolean marginFloorTriggered;
        private BigDecimal marginFloorAdjustment;
        private java.util.UUID appliedMarginRuleId;
        private BigDecimal subtotal;
        private BigDecimal gstRatePercent;
        private BigDecimal gstAmount;
        private BigDecimal finalPrice;

        Result(PriceCalculationResult r) {
            productId = r.productId();
            quantity = r.quantity();
            hsnCode = r.hsnCode();
            basePrice = r.basePrice();
            basePriceTotal = r.basePriceTotal();
            appliedTierId = r.appliedTierId();
            tierUnitPrice = r.tierUnitPrice();
            tierAdjustedTotal = r.tierAdjustedTotal();
            appliedContractId = r.appliedContractId();
            contractUnitPrice = r.contractUnitPrice();
            contractAdjustedTotal = r.contractAdjustedTotal();
            appliedCouponId = r.appliedCouponId();
            couponDiscountAmount = r.couponDiscountAmount();
            preFloorPrice = r.preFloorPrice();
            marginFloorTriggered = r.marginFloorTriggered();
            marginFloorAdjustment = r.marginFloorAdjustment();
            appliedMarginRuleId = r.appliedMarginRuleId();
            subtotal = r.subtotal();
            gstRatePercent = r.gstRatePercent();
            gstAmount = r.gstAmount();
            finalPrice = r.finalPrice();
        }

        Result appliedTierId(java.util.UUID v) { this.appliedTierId = v; return this; }
        Result tierUnitPrice(BigDecimal v) { this.tierUnitPrice = v; return this; }
        Result tierAdjustedTotal(BigDecimal v) { this.tierAdjustedTotal = v; return this; }
        Result appliedContractId(java.util.UUID v) { this.appliedContractId = v; return this; }
        Result contractUnitPrice(BigDecimal v) { this.contractUnitPrice = v; return this; }
        Result contractAdjustedTotal(BigDecimal v) { this.contractAdjustedTotal = v; return this; }
        Result appliedCouponId(java.util.UUID v) { this.appliedCouponId = v; return this; }
        Result couponDiscountAmount(BigDecimal v) { this.couponDiscountAmount = v; return this; }
        Result preFloorPrice(BigDecimal v) { this.preFloorPrice = v; return this; }
        Result marginFloorTriggered(boolean v) { this.marginFloorTriggered = v; return this; }
        Result marginFloorAdjustment(BigDecimal v) { this.marginFloorAdjustment = v; return this; }
        Result appliedMarginRuleId(java.util.UUID v) { this.appliedMarginRuleId = v; return this; }
        Result subtotal(BigDecimal v) { this.subtotal = v; return this; }
        Result gstRatePercent(BigDecimal v) { this.gstRatePercent = v; return this; }
        Result gstAmount(BigDecimal v) { this.gstAmount = v; return this; }
        Result finalPrice(BigDecimal v) { this.finalPrice = v; return this; }

        PriceCalculationResult build() {
            return new PriceCalculationResult(
                    productId, quantity, hsnCode,
                    basePrice, basePriceTotal,
                    appliedTierId, tierUnitPrice, tierAdjustedTotal,
                    appliedContractId, contractUnitPrice, contractAdjustedTotal,
                    appliedCouponId, couponDiscountAmount,
                    preFloorPrice, marginFloorTriggered, marginFloorAdjustment, appliedMarginRuleId,
                    subtotal,
                    gstRatePercent, gstAmount,
                    finalPrice
            );
        }
    }
}
