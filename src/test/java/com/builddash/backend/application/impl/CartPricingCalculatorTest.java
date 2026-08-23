package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.PricingCalculator;
import com.builddash.backend.domain.enums.DiscountType;
import com.builddash.backend.domain.model.Cart;
import com.builddash.backend.domain.model.CartLineItem;
import com.builddash.backend.domain.model.Coupon;
import com.builddash.backend.domain.model.PriceCalculationResult;
import com.builddash.backend.domain.model.PricedCart;
import com.builddash.backend.domain.model.PricingRequest;
import com.builddash.backend.domain.port.CartPricingCalculator;
import com.builddash.backend.domain.port.CouponRedemptionRepository;
import com.builddash.backend.domain.port.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CartPricingCalculatorTest {

    private PricingCalculator pricingCalculator;
    private CouponRepository couponRepository;
    private CouponRedemptionRepository couponRedemptionRepository;
    private CartPricingCalculator cartPricingCalculator;

    @BeforeEach
    void setUp() {
        pricingCalculator = mock(PricingCalculator.class);
        couponRepository = mock(CouponRepository.class);
        couponRedemptionRepository = mock(CouponRedemptionRepository.class);
        cartPricingCalculator = new CartPricingCalculatorImpl(
                pricingCalculator,
                couponRepository,
                couponRedemptionRepository
        );
    }

    @ParameterizedTest(name = "Cart total {0} with min order {1} -> Coupon applied={2}, reason={3}")
    @CsvSource({
            "6000.00, 5000.00, true, null",
            "4000.00, 5000.00, false, MIN_ORDER_VALUE_NOT_MET"
    })
    void calculate_minOrderValueValidation(BigDecimal cartTotal, BigDecimal minOrderValue, boolean shouldApply, String expectedReason) {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String couponCode = "MIN5000";

        CartLineItem item = new CartLineItem(UUID.randomUUID(), UUID.randomUUID(), productId, 1, null);
        Cart cart = new Cart(UUID.randomUUID(), userId, null, couponCode, List.of(item));

        PriceCalculationResult itemResult = new PriceCalculationResult(
                productId, 1, "1234", cartTotal, cartTotal,
                null, null, null, null, null, null,
                null, BigDecimal.ZERO, null, false, BigDecimal.ZERO, null,
                cartTotal, null, BigDecimal.ZERO, cartTotal
        );

        when(pricingCalculator.calculate(any(PricingRequest.class))).thenReturn(itemResult);

        Coupon coupon = new Coupon(
                UUID.randomUUID(), couponCode, DiscountType.FLAT, new BigDecimal("500.00"),
                minOrderValue, Instant.now().plus(1, ChronoUnit.DAYS), 5, List.of(), false, true,
                Instant.now(), Instant.now()
        );
        when(couponRepository.findByCode(couponCode)).thenReturn(Optional.of(coupon));
        when(couponRedemptionRepository.countByUserAndCoupon(eq(userId), any(UUID.class))).thenReturn(0);

        PricedCart pricedCart = cartPricingCalculator.calculate(cart, userId);

        if (shouldApply) {
            assertThat(pricedCart.appliedCartCoupon()).isEqualTo(couponCode);
            assertThat(pricedCart.cartDiscountTotal()).isEqualByComparingTo("500.00");
            assertThat(pricedCart.couponDroppedReason()).isNull();
        } else {
            assertThat(pricedCart.appliedCartCoupon()).isNull();
            assertThat(pricedCart.cartDiscountTotal()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(pricedCart.couponDroppedReason()).isEqualTo("MIN_ORDER_VALUE_NOT_MET");
        }
    }

    @Test
    void calculate_whenCouponExpired_dropsWithReason() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String couponCode = "EXPIRED10";

        CartLineItem item = new CartLineItem(UUID.randomUUID(), UUID.randomUUID(), productId, 1, null);
        Cart cart = new Cart(UUID.randomUUID(), userId, null, couponCode, List.of(item));

        BigDecimal cartTotal = new BigDecimal("1000.00");
        PriceCalculationResult itemResult = new PriceCalculationResult(
                productId, 1, "1234", cartTotal, cartTotal,
                null, null, null, null, null, null,
                null, BigDecimal.ZERO, null, false, BigDecimal.ZERO, null,
                cartTotal, null, BigDecimal.ZERO, cartTotal
        );
        when(pricingCalculator.calculate(any())).thenReturn(itemResult);

        Coupon expiredCoupon = new Coupon(
                UUID.randomUUID(), couponCode, DiscountType.PERCENT, new BigDecimal("10.00"),
                null, Instant.now().minus(1, ChronoUnit.DAYS), 5, List.of(), false, true,
                Instant.now(), Instant.now()
        );
        when(couponRepository.findByCode(couponCode)).thenReturn(Optional.of(expiredCoupon));

        PricedCart pricedCart = cartPricingCalculator.calculate(cart, userId);

        assertThat(pricedCart.appliedCartCoupon()).isNull();
        assertThat(pricedCart.couponDroppedReason()).isEqualTo("COUPON_EXPIRED");
        assertThat(pricedCart.cartDiscountTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
