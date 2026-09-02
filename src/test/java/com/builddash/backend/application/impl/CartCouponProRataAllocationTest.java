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
import com.builddash.backend.domain.port.CouponRedemptionRepository;
import com.builddash.backend.domain.port.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * H4.2 Pro-rata cart coupon discount allocation & largest-remainder rounding tests.
 * Guarantees:
 *   sum(lineFinalTotal) == order.totalAmount
 *   sum(lineTax) == order.totalTax
 *   sum(allocatedDiscount) == cartDiscount
 */
@ExtendWith(MockitoExtension.class)
class CartCouponProRataAllocationTest {

    @Mock
    private PricingCalculator pricingCalculator;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponRedemptionRepository couponRedemptionRepository;

    private CartPricingCalculatorImpl calculator;

    private final UUID userId = UUID.randomUUID();
    private final UUID product1Id = UUID.randomUUID();
    private final UUID product2Id = UUID.randomUUID();
    private final UUID product3Id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        calculator = new CartPricingCalculatorImpl(pricingCalculator, couponRepository, couponRedemptionRepository);
    }

    private PriceCalculationResult mockResult(UUID productId, int quantity, BigDecimal basePrice, BigDecimal gstRatePercent) {
        BigDecimal basePriceTotal = basePrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal subtotal = basePriceTotal;
        BigDecimal gstAmount = subtotal.multiply(gstRatePercent).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal finalPrice = subtotal.add(gstAmount);

        return new PriceCalculationResult(
                productId, quantity, "HSN123",
                basePrice, basePriceTotal,
                null, null, basePriceTotal,
                null, null, basePriceTotal,
                null, BigDecimal.ZERO,
                basePriceTotal, false, BigDecimal.ZERO, null,
                subtotal,
                gstRatePercent, gstAmount,
                finalPrice
        );
    }

    @Test
    void flatCartCoupon_allocatedProRata_withLargestRemainderDistribution() {
        // 3 items:
        // Item 1: 1 x 1000 = 1000 (18% GST)
        // Item 2: 1 x 1000 = 1000 (18% GST)
        // Item 3: 1 x 1000 = 1000 (18% GST)
        // Subtotal = 3000. Cart Coupon = FLAT 100.
        // Each item exact discount = 33.3333... -> 33.33 each with remainder 0.01 on first item -> 33.34, 33.33, 33.33. Total discount = 100.00.
        when(pricingCalculator.calculate(any(PricingRequest.class))).thenAnswer(inv -> {
            PricingRequest req = inv.getArgument(0);
            return mockResult(req.productId(), req.quantity(), new BigDecimal("1000.00"), new BigDecimal("18.00"));
        });

        Coupon coupon = new Coupon(UUID.randomUUID(), "FLAT100", DiscountType.FLAT, new BigDecimal("100.00"),
                null, Instant.now().plusSeconds(3600), null, List.of(), true, true, Instant.now(), Instant.now());
        when(couponRepository.findByCode("FLAT100")).thenReturn(Optional.of(coupon));

        Cart cart = new Cart(UUID.randomUUID(), userId, null, null, "FLAT100", List.of(
                new CartLineItem(UUID.randomUUID(), null, product1Id, 1, null),
                new CartLineItem(UUID.randomUUID(), null, product2Id, 1, null),
                new CartLineItem(UUID.randomUUID(), null, product3Id, 1, null)
        ));

        PricedCart priced = calculator.calculate(cart, userId);

        assertThat(priced.cartDiscountTotal()).isEqualByComparingTo("100.00");

        BigDecimal sumAllocatedDiscounts = priced.items().stream()
                .map(PricedCartLineItem::allocatedDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumAllocatedDiscounts).isEqualByComparingTo("100.00");

        BigDecimal sumLineTaxes = priced.items().stream()
                .map(PricedCartLineItem::lineGst)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumLineTaxes).isEqualByComparingTo(priced.totalGst());

        BigDecimal sumLineFinalTotals = priced.items().stream()
                .map(PricedCartLineItem::lineFinalTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumLineFinalTotals).isEqualByComparingTo(priced.finalTotal());

        // Net base after discount: 3000 - 100 = 2900. Total tax @ 18% = 522.00. Final total = 3422.00.
        assertThat(priced.finalTotal()).isEqualByComparingTo("3422.00");
    }

    @Test
    void percentCartCoupon_allocatedProRata_acrossMixedTaxRates() {
        // Item 1: 1 x 500 @ 28% GST
        // Item 2: 1 x 1500 @ 18% GST
        // Subtotal = 2000. Cart Coupon = 10% (Discount = 200.00).
        // Item 1 gets 500/2000 * 200 = 50.00 discount -> Net base = 450.00. Tax @ 28% = 126.00. Line Total = 576.00.
        // Item 2 gets 1500/2000 * 200 = 150.00 discount -> Net base = 1350.00. Tax @ 18% = 243.00. Line Total = 1593.00.
        // Sum Line Total = 576.00 + 1593.00 = 2169.00.
        when(pricingCalculator.calculate(any(PricingRequest.class))).thenAnswer(inv -> {
            PricingRequest req = inv.getArgument(0);
            if (req.productId().equals(product1Id)) {
                return mockResult(product1Id, 1, new BigDecimal("500.00"), new BigDecimal("28.00"));
            } else {
                return mockResult(product2Id, 1, new BigDecimal("1500.00"), new BigDecimal("18.00"));
            }
        });

        Coupon coupon = new Coupon(UUID.randomUUID(), "SAVE10", DiscountType.PERCENT, new BigDecimal("10.00"),
                null, Instant.now().plusSeconds(3600), null, List.of(), true, true, Instant.now(), Instant.now());
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));

        Cart cart = new Cart(UUID.randomUUID(), userId, null, null, "SAVE10", List.of(
                new CartLineItem(UUID.randomUUID(), null, product1Id, 1, null),
                new CartLineItem(UUID.randomUUID(), null, product2Id, 1, null)
        ));

        PricedCart priced = calculator.calculate(cart, userId);

        assertThat(priced.cartDiscountTotal()).isEqualByComparingTo("200.00");

        BigDecimal sumAllocatedDiscounts = priced.items().stream()
                .map(PricedCartLineItem::allocatedDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumAllocatedDiscounts).isEqualByComparingTo("200.00");

        BigDecimal sumLineFinalTotals = priced.items().stream()
                .map(PricedCartLineItem::lineFinalTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumLineFinalTotals).isEqualByComparingTo(priced.finalTotal());
        assertThat(priced.finalTotal()).isEqualByComparingTo("2169.00");
    }

    @Test
    void nonStackableCartCoupon_withItemCouponApplied_dropsCartCoupon() {
        when(pricingCalculator.calculate(any(PricingRequest.class))).thenAnswer(inv -> {
            PricingRequest req = inv.getArgument(0);
            return mockResult(req.productId(), req.quantity(), new BigDecimal("1000.00"), new BigDecimal("18.00"));
        });

        Coupon coupon = new Coupon(UUID.randomUUID(), "CARTNONSTACK", DiscountType.FLAT, new BigDecimal("100.00"),
                null, Instant.now().plusSeconds(3600), null, List.of(), false, true, Instant.now(), Instant.now());
        when(couponRepository.findByCode("CARTNONSTACK")).thenReturn(Optional.of(coupon));

        Cart cart = new Cart(UUID.randomUUID(), userId, null, null, "CARTNONSTACK", List.of(
                new CartLineItem(UUID.randomUUID(), null, product1Id, 1, "ITEM_COUPON")
        ));

        PricedCart priced = calculator.calculate(cart, userId);

        assertThat(priced.cartDiscountTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(priced.couponDroppedReason()).isEqualTo("NON_STACKABLE");
    }
}
