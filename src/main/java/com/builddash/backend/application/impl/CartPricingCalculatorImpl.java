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
import com.builddash.backend.domain.service.CouponValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
        if (cart == null || cart.items() == null || cart.items().isEmpty()) {
            return new PricedCart(
                    cart != null ? cart.id() : null,
                    cart != null ? cart.userId() : null,
                    cart != null ? cart.projectId() : null,
                    List.of(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    null,
                    null,
                    cart != null ? cart.companyId() : null
            );
        }

        List<PriceCalculationResult> itemResults = new ArrayList<>();
        List<BigDecimal> lineTaxableBases = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal itemDiscountsTotal = BigDecimal.ZERO;
        boolean hasItemCoupons = false;

        for (CartLineItem item : cart.items()) {
            PricingRequest request = new PricingRequest(
                    item.productId(),
                    item.quantity(),
                    userId,
                    item.appliedItemCoupon(),
                    cart.companyId(),
                    item.unitPriceOverride()
            );

            PriceCalculationResult itemResult = pricingCalculator.calculate(request);
            itemResults.add(itemResult);

            BigDecimal lineSubtotal = itemResult.basePriceTotal();
            BigDecimal lineDiscount = itemResult.basePriceTotal().subtract(itemResult.subtotal());
            BigDecimal lineTaxableBase = itemResult.subtotal();

            subtotal = subtotal.add(lineSubtotal);
            itemDiscountsTotal = itemDiscountsTotal.add(lineDiscount);
            lineTaxableBases.add(lineTaxableBase);

            if (item.appliedItemCoupon() != null && !item.appliedItemCoupon().isBlank()) {
                hasItemCoupons = true;
            }
        }

        BigDecimal cartEligibleAmount = subtotal.subtract(itemDiscountsTotal).max(BigDecimal.ZERO);

        // Cart-level coupon evaluation via canonical CouponValidator (H4.1)
        BigDecimal cartDiscountTotal = BigDecimal.ZERO;
        String couponDroppedReason = null;
        String appliedCartCoupon = cart.appliedCartCoupon();

        if (appliedCartCoupon != null && !appliedCartCoupon.isBlank()) {
            Optional<Coupon> couponOpt = couponRepository.findByCode(appliedCartCoupon);
            if (couponOpt.isEmpty()) {
                couponDroppedReason = "COUPON_NOT_FOUND";
            } else {
                Coupon coupon = couponOpt.get();
                int userRedemptions = (userId != null)
                        ? couponRedemptionRepository.countByUserAndCoupon(userId, coupon.getId())
                        : 0;

                CouponValidator.ValidationResult validation = CouponValidator.validate(
                        coupon,
                        null,
                        cartEligibleAmount,
                        userRedemptions,
                        hasItemCoupons,
                        Instant.now()
                );

                if (!validation.valid()) {
                    couponDroppedReason = validation.dropReason().name();
                } else {
                    if (coupon.getDiscountType() == DiscountType.PERCENT) {
                        cartDiscountTotal = cartEligibleAmount.multiply(coupon.getDiscountValue())
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    } else { // FLAT
                        cartDiscountTotal = coupon.getDiscountValue().min(cartEligibleAmount);
                    }
                }
            }
        }

        // H4.2: Pro-rata cart coupon discount allocation (largest-remainder method)
        int itemCount = cart.items().size();
        BigDecimal[] allocatedDiscounts = new BigDecimal[itemCount];
        for (int i = 0; i < itemCount; i++) {
            allocatedDiscounts[i] = BigDecimal.ZERO;
        }

        if (cartDiscountTotal.compareTo(BigDecimal.ZERO) > 0 && cartEligibleAmount.compareTo(BigDecimal.ZERO) > 0) {
            record AllocationRemainder(int index, long floorCents, double fraction) {}
            List<AllocationRemainder> remainders = new ArrayList<>();
            long totalFloorCents = 0;

            for (int i = 0; i < itemCount; i++) {
                BigDecimal lineBase = lineTaxableBases.get(i);
                if (lineBase.compareTo(BigDecimal.ZERO) <= 0) {
                    remainders.add(new AllocationRemainder(i, 0, 0.0));
                    continue;
                }

                // exact cents = (cartDiscountTotal * lineBase / cartEligibleAmount) * 100
                BigDecimal exactCents = cartDiscountTotal.multiply(lineBase)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(cartEligibleAmount, 8, RoundingMode.HALF_UP);

                long floorCents = exactCents.longValue();
                double fraction = exactCents.subtract(BigDecimal.valueOf(floorCents)).doubleValue();

                remainders.add(new AllocationRemainder(i, floorCents, fraction));
                totalFloorCents += floorCents;
            }

            long targetTotalCents = cartDiscountTotal.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
            long remCents = targetTotalCents - totalFloorCents;

            // Sort lines in descending order of fractional remainder (stable tie-break by index)
            remainders.sort(Comparator.comparingDouble(AllocationRemainder::fraction).reversed()
                    .thenComparingInt(AllocationRemainder::index));

            for (int r = 0; r < remainders.size(); r++) {
                AllocationRemainder ar = remainders.get(r);
                long cents = ar.floorCents + (r < remCents ? 1 : 0);
                allocatedDiscounts[ar.index] = BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);
            }
        }

        // Construct PricedCartLineItems with exact line taxable bases, line GST, and line totals
        List<PricedCartLineItem> pricedItems = new ArrayList<>();
        BigDecimal totalGst = BigDecimal.ZERO;
        BigDecimal finalTotal = BigDecimal.ZERO;

        for (int i = 0; i < itemCount; i++) {
            CartLineItem item = cart.items().get(i);
            PriceCalculationResult itemResult = itemResults.get(i);

            BigDecimal lineSubtotal = itemResult.basePriceTotal();
            BigDecimal lineDiscount = itemResult.basePriceTotal().subtract(itemResult.subtotal());
            BigDecimal allocatedDiscount = allocatedDiscounts[i];

            BigDecimal netLineTaxableBase = itemResult.subtotal().subtract(allocatedDiscount).max(BigDecimal.ZERO);
            BigDecimal gstRatePercent = itemResult.gstRatePercent() != null ? itemResult.gstRatePercent() : BigDecimal.ZERO;
            BigDecimal lineGst = netLineTaxableBase.multiply(gstRatePercent)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal lineFinalTotal = netLineTaxableBase.add(lineGst);
            BigDecimal unitFinalPrice = lineFinalTotal.divide(BigDecimal.valueOf(item.quantity()), 2, RoundingMode.HALF_UP);

            PricedCartLineItem pricedItem = new PricedCartLineItem(
                    item.productId(),
                    item.quantity(),
                    itemResult.hsnCode(),
                    itemResult.basePrice(),
                    unitFinalPrice,
                    lineSubtotal,
                    lineDiscount,
                    lineGst,
                    lineFinalTotal,
                    item.appliedItemCoupon(),
                    gstRatePercent,
                    allocatedDiscount
            );

            pricedItems.add(pricedItem);
            totalGst = totalGst.add(lineGst);
            finalTotal = finalTotal.add(lineFinalTotal);
        }

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
                couponDroppedReason,
                cart.companyId()
        );
    }
}
