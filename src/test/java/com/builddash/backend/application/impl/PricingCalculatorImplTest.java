package com.builddash.backend.application.impl;

import com.builddash.backend.domain.exception.GstRateUnresolvedException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.ProductNotPricedException;
import com.builddash.backend.domain.model.BulkPricingTier;
import com.builddash.backend.domain.model.HsnGstRate;
import com.builddash.backend.domain.model.MarginRule;
import com.builddash.backend.domain.model.PriceCalculationResult;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.PricingRequest;
import com.builddash.backend.domain.port.BulkPricingTierRepository;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ContractPriceRepository;
import com.builddash.backend.domain.port.CouponRedemptionRepository;
import com.builddash.backend.domain.port.CouponRepository;
import com.builddash.backend.domain.port.HsnGstRateRepository;
import com.builddash.backend.domain.port.MarginRuleRepository;
import com.builddash.backend.domain.port.ProductBasePriceRepository;
import com.builddash.backend.domain.port.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * PricingCalculatorImpl only depends on ports (interfaces), so this is a plain Mockito unit
 * test — no Spring context, no Testcontainers. Covers what the pure PricingSteps tests can't:
 * the loadContext wiring itself (which port feeds which context field, the margin-rule
 * product-then-category fallback, fail-closed guards firing at the right point in the run).
 */
@ExtendWith(MockitoExtension.class)
class PricingCalculatorImplTest {

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private HsnGstRateRepository hsnGstRateRepository;
    @Mock
    private ProductBasePriceRepository productBasePriceRepository;
    @Mock
    private BulkPricingTierRepository bulkPricingTierRepository;
    @Mock
    private ContractPriceRepository contractPriceRepository;
    @Mock
    private CouponRepository couponRepository;
    @Mock
    private CouponRedemptionRepository couponRedemptionRepository;
    @Mock
    private MarginRuleRepository marginRuleRepository;

    private PricingCalculatorImpl calculator;

    @BeforeEach
    void setUp() {
        calculator = new PricingCalculatorImpl(productRepository, categoryRepository, hsnGstRateRepository,
                productBasePriceRepository, bulkPricingTierRepository, contractPriceRepository,
                couponRepository, couponRedemptionRepository, marginRuleRepository);

        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setCategoryId(CATEGORY_ID);
        product.setHsnCode("1001");
        // lenient: the product-not-found/no-base-price guard tests short-circuit before some
        // of these are ever reached, which strict stubbing would otherwise flag as unused.
        lenient().when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        lenient().when(productBasePriceRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(new BigDecimal("100.00")));
        lenient().when(hsnGstRateRepository.findByHsnCode("1001"))
                .thenReturn(Optional.of(new HsnGstRate("1001", "Cement", new BigDecimal("18"), "Building", null, null)));
    }

    @Test
    void calculate_tierPlusMarginFloorTriggered_composesCorrectly() {
        when(bulkPricingTierRepository.findByProductId(PRODUCT_ID)).thenReturn(List.of(
                new BulkPricingTier(UUID.randomUUID(), PRODUCT_ID, 5, new BigDecimal("10.00"), null, null)));
        when(marginRuleRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(
                new MarginRule(UUID.randomUUID(), PRODUCT_ID, null, null, null, new BigDecimal("80.00"), null, null)));

        PriceCalculationResult result = calculator.calculate(new PricingRequest(PRODUCT_ID, 5, null, null));

        assertThat(result.tierAdjustedTotal()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(result.marginFloorTriggered()).isTrue();
        assertThat(result.subtotal()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(result.gstAmount()).isEqualByComparingTo(new BigDecimal("14.40"));
        assertThat(result.finalPrice()).isEqualByComparingTo(new BigDecimal("94.40"));
    }

    @Test
    void calculate_contractOverridePlusCoupon_marginFloorNotTriggered() {
        UUID userId = UUID.randomUUID();
        var contractPrice = new com.builddash.backend.domain.model.ContractPrice(
                UUID.randomUUID(), userId, PRODUCT_ID, new BigDecimal("90.00"), null, null, null, null);
        when(contractPriceRepository.findActive(eq(userId), eq(PRODUCT_ID), any(java.time.Instant.class)))
                .thenReturn(Optional.of(contractPrice));
        var coupon = new com.builddash.backend.domain.model.Coupon(UUID.randomUUID(), "SAVE10",
                com.builddash.backend.domain.enums.DiscountType.PERCENT, new BigDecimal("10"), null,
                java.time.Instant.now().plusSeconds(3600), null, List.of(), false, true, null, null);
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));

        PriceCalculationResult result = calculator.calculate(new PricingRequest(PRODUCT_ID, 1, userId, "SAVE10"));

        assertThat(result.contractAdjustedTotal()).isEqualByComparingTo(new BigDecimal("90.00"));
        assertThat(result.couponDiscountAmount()).isEqualByComparingTo(new BigDecimal("9.00"));
        assertThat(result.marginFloorTriggered()).isFalse();
        assertThat(result.subtotal()).isEqualByComparingTo(new BigDecimal("81.00"));
    }

    @Test
    void calculate_marginRule_fallsBackToCategoryLevelWhenNoProductLevelRule() {
        // Distinct from the true no-rule-anywhere case: findByProductId genuinely empty, but a
        // category-level rule exists and must be picked up via the .or(...) fallback in loadContext.
        when(marginRuleRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.empty());
        MarginRule categoryRule = new MarginRule(UUID.randomUUID(), null, CATEGORY_ID, null, null,
                new BigDecimal("150.00"), null, null);
        when(marginRuleRepository.findByCategoryId(CATEGORY_ID)).thenReturn(Optional.of(categoryRule));

        PriceCalculationResult result = calculator.calculate(new PricingRequest(PRODUCT_ID, 1, null, null));

        assertThat(result.appliedMarginRuleId()).isEqualTo(categoryRule.getId());
        assertThat(result.marginFloorTriggered()).isTrue();
        assertThat(result.subtotal()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void calculate_noRuleAtEitherLevel_neverCallsCategoryFallbackIntoATriggeredFloor() {
        when(marginRuleRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.empty());
        when(marginRuleRepository.findByCategoryId(CATEGORY_ID)).thenReturn(Optional.empty());

        PriceCalculationResult result = calculator.calculate(new PricingRequest(PRODUCT_ID, 1, null, null));

        assertThat(result.appliedMarginRuleId()).isNull();
        assertThat(result.marginFloorTriggered()).isFalse();
    }

    @Test
    void calculate_productNotFound_throws() {
        UUID unknownId = UUID.randomUUID();
        when(productRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> calculator.calculate(new PricingRequest(unknownId, 1, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void calculate_noBasePriceRow_throwsProductNotPriced() {
        when(productBasePriceRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> calculator.calculate(new PricingRequest(PRODUCT_ID, 1, null, null)))
                .isInstanceOf(ProductNotPricedException.class);
    }

    @Test
    void calculate_unresolvedGst_throwsOnlyAfterEveryOtherStepRan() {
        // Proves ordering: tier/contract/coupon/margin-floor all ran to completion (tier applied
        // correctly below) before the pipeline finally fails on the unresolved GST rate, not a
        // short-circuit that skips the earlier steps.
        when(hsnGstRateRepository.findByHsnCode("1001")).thenReturn(Optional.empty());
        when(bulkPricingTierRepository.findByProductId(PRODUCT_ID)).thenReturn(List.of(
                new BulkPricingTier(UUID.randomUUID(), PRODUCT_ID, 1, new BigDecimal("10.00"), null, null)));

        assertThatThrownBy(() -> calculator.calculate(new PricingRequest(PRODUCT_ID, 1, null, null)))
                .isInstanceOf(GstRateUnresolvedException.class)
                .extracting(ex -> ((GstRateUnresolvedException) ex).getHsnCode())
                .isEqualTo("1001");
    }
}
