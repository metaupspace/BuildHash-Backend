package com.builddash.backend.domain.service;

import com.builddash.backend.domain.model.Coupon;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class CouponValidator {

    private CouponValidator() {
    }

    public enum DropReason {
        COUPON_NOT_FOUND,
        COUPON_INACTIVE,
        COUPON_EXPIRED,
        MAX_USES_EXCEEDED,
        CATEGORY_INELIGIBLE,
        MIN_ORDER_VALUE_NOT_MET,
        NON_STACKABLE
    }

    public record ValidationResult(boolean valid, DropReason dropReason) {
        public static ValidationResult validResult() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult dropped(DropReason reason) {
            return new ValidationResult(false, reason);
        }
    }

    public static ValidationResult validate(
            Coupon coupon,
            UUID categoryId,
            BigDecimal eligibleAmount,
            Integer userRedemptionCount,
            boolean hasOtherAppliedCoupons,
            Instant asOf
    ) {
        if (coupon == null) {
            return ValidationResult.dropped(DropReason.COUPON_NOT_FOUND);
        }
        if (!coupon.isActive()) {
            return ValidationResult.dropped(DropReason.COUPON_INACTIVE);
        }
        if (coupon.getExpiresAt() != null && coupon.getExpiresAt().isBefore(asOf)) {
            return ValidationResult.dropped(DropReason.COUPON_EXPIRED);
        }
        if (coupon.getMaxUsesPerUser() != null && userRedemptionCount != null
                && userRedemptionCount >= coupon.getMaxUsesPerUser()) {
            return ValidationResult.dropped(DropReason.MAX_USES_EXCEEDED);
        }
        if (coupon.getEligibleCategoryIds() != null && !coupon.getEligibleCategoryIds().isEmpty()
                && (categoryId == null || !coupon.getEligibleCategoryIds().contains(categoryId))) {
            return ValidationResult.dropped(DropReason.CATEGORY_INELIGIBLE);
        }
        if (coupon.getMinOrderValue() != null && eligibleAmount != null
                && eligibleAmount.compareTo(coupon.getMinOrderValue()) < 0) {
            return ValidationResult.dropped(DropReason.MIN_ORDER_VALUE_NOT_MET);
        }
        if (!coupon.isStackable() && hasOtherAppliedCoupons) {
            return ValidationResult.dropped(DropReason.NON_STACKABLE);
        }

        return ValidationResult.validResult();
    }
}
