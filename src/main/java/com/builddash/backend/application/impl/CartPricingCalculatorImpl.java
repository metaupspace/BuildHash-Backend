package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.PricingCalculator;
import com.builddash.backend.domain.enums.DiscountType;
import com.builddash.backend.domain.model.Cart;
import com.builddash.backend.domain.model.CartLineItem;
import com.builddash.backend.domain.model.Coupon;
import com.builddash.backend.domain.model.PriceCalculationResult;
import com.builddash.backend.domain.model.PricedCart;
import com.builddash.backend.domain.model.PricedCartLineItem;
import com.builddash.backend.domain.model.PricingRequest;
import com.builddash.backend.domain.port.CartPricingCalculator;
import com.builddash.backend.domain.port.CouponRedemptionRepository;
import com.builddash.backend.domain.port.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartPricingCalculatorImpl implements CartPricingCalculator {

    private final PricingCalculator pricingCalculator;
    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;

    @Override
    public PricedCart calculate(Cart cart, UUID userId) {
        List<PricedCartLineItem> pricedItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal itemDiscountsTotal = BigDecimal.ZERO;
        BigDecimal totalGst = BigDecimal.ZERO;

        for (CartLineItem item : cart.items()) {
            PricingRequest request = new PricingRequest(
                    item.productId(),
                    item.quantity(),
                    userId,
                    item.appliedItemCoupon()
            );

            PriceCalculationResult itemResult = pricingCalculator.calculate(request);

            BigDecimal lineSubtotal = itemResult.basePriceTotal();
            BigDecimal lineDiscount = itemResult.basePriceTotal().subtract(itemResult.subtotal());
            BigDecimal lineGst = itemResult.gstAmount() != null ? itemResult.gstAmount() : BigDecimal.ZERO;
            BigDecimal lineFinalTotal = itemResult.finalPrice() != null ? itemResult.finalPrice() : itemResult.subtotal().add(lineGst);

            PricedCartLineItem pricedItem = new PricedCartLineItem(
                    item.productId(),
                    item.quantity(),
                    itemResult.hsnCode(),
                    itemResult.basePrice(),
                    itemResult.finalPrice() != null ? itemResult.finalPrice().divide(BigDecimal.valueOf(item.quantity()), 2, RoundingMode.HALF_UP) : itemResult.basePrice(),
                    lineSubtotal,
                    lineDiscount,
                    lineGst,
                    lineFinalTotal,
                    item.appliedItemCoupon()
            );

            pricedItems.add(pricedItem);
            subtotal = subtotal.add(lineSubtotal);
            itemDiscountsTotal = itemDiscountsTotal.add(lineDiscount);
            totalGst = totalGst.add(lineGst);
        }

        // Cart-level coupon evaluation
        BigDecimal cartDiscountTotal = BigDecimal.ZERO;
        String couponDroppedReason = null;
        String appliedCartCoupon = cart.appliedCartCoupon();

        if (appliedCartCoupon != null && !appliedCartCoupon.isBlank()) {
            Optional<Coupon> couponOpt = couponRepository.findByCode(appliedCartCoupon);
            if (couponOpt.isEmpty()) {
                couponDroppedReason = "COUPON_NOT_FOUND";
            } else {
                Coupon coupon = couponOpt.get();
                Instant now = Instant.now();

                if (!coupon.isActive()) {
                    couponDroppedReason = "COUPON_INACTIVE";
                } else if (coupon.getExpiresAt().isBefore(now)) {
                    couponDroppedReason = "COUPON_EXPIRED";
                } else if (userId != null && coupon.getMaxUsesPerUser() != null &&
                        couponRedemptionRepository.countByUserAndCoupon(userId, coupon.getId()) >= coupon.getMaxUsesPerUser()) {
                    couponDroppedReason = "MAX_USES_EXCEEDED";
                } else if (coupon.getMinOrderValue() != null && subtotal.subtract(itemDiscountsTotal).compareTo(coupon.getMinOrderValue()) < 0) {
                    couponDroppedReason = "MIN_ORDER_VALUE_NOT_MET";
                } else {
                    // Valid coupon, compute discount
                    BigDecimal cartEligibleAmount = subtotal.subtract(itemDiscountsTotal);
                    if (coupon.getDiscountType() == DiscountType.PERCENT) {
                        cartDiscountTotal = cartEligibleAmount.multiply(coupon.getDiscountValue())
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    } else { // FLAT
                        cartDiscountTotal = coupon.getDiscountValue().min(cartEligibleAmount);
                    }
                }
            }
        }

        BigDecimal netBeforeGst = subtotal.subtract(itemDiscountsTotal).subtract(cartDiscountTotal).max(BigDecimal.ZERO);
        BigDecimal finalTotal = netBeforeGst.add(totalGst);

        return new PricedCart(
                cart.id(),
                cart.userId(),
                cart.projectId(),
                pricedItems,
                subtotal,
                itemDiscountsTotal,
                cartDiscountTotal,
                totalGst,
                finalTotal,
                cartDiscountTotal.compareTo(BigDecimal.ZERO) > 0 ? appliedCartCoupon : null,
                couponDroppedReason
        );
    }
}
