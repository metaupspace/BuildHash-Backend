package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.DiscountType;
import com.builddash.backend.domain.exception.BadRequestException;
import com.builddash.backend.domain.exception.GstRateUnresolvedException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.BulkPricingTier;
import com.builddash.backend.domain.model.ContractPrice;
import com.builddash.backend.domain.model.Coupon;
import com.builddash.backend.domain.model.MarginRule;
import com.builddash.backend.domain.model.PriceCalculationResult;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.PricingRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Steps are pure static functions — no Spring context, no mocks, plain data in/out. Each test
 * builds exactly the PricingContext the step under test reads and asserts on the returned
 * record. See PLAN_PHASE2.md Section 6 and PROGRESS.md's Checkpoint C entry for the coverage
 * rationale (per-step boundary coverage + a small composed set, not a full cross-product).
 */
class PricingStepsTest {

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final BigDecimal BASE_PRICE = new BigDecimal("100.00");
    private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");

    private static Product product() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setCategoryId(CATEGORY_ID);
        product.setHsnCode("1001");
        return product;
    }

    private static PriceCalculationResult initial(int quantity) {
        return PriceCalculationResult.initial(new PricingRequest(PRODUCT_ID, quantity, null, null), "1001", BASE_PRICE);
    }

    private static BulkPricingTier tier(int minQuantity, String unitPrice) {
        return new BulkPricingTier(UUID.randomUUID(), PRODUCT_ID, minQuantity, new BigDecimal(unitPrice), NOW, NOW);
    }

    // ---- applyBulkTier ----

    @ParameterizedTest(name = "{0}")
    @MethodSource("bulkTierCases")
    void applyBulkTier_selectsClosestBelowOrPassesThrough(String caseName, List<BulkPricingTier> tiers,
                                                            int quantity, Integer expectedTierIndex) {
        PricingContext ctx = new ContextBuilder().tiers(tiers).build();
        PriceCalculationResult result = PricingSteps.applyBulkTier(initial(quantity), ctx);

        if (expectedTierIndex == null) {
            assertThat(result.appliedTierId()).isNull();
            assertThat(result.tierAdjustedTotal()).isEqualByComparingTo(result.basePriceTotal());
        } else {
            BulkPricingTier expected = tiers.get(expectedTierIndex);
            assertThat(result.appliedTierId()).isEqualTo(expected.getId());
            assertThat(result.tierUnitPrice()).isEqualByComparingTo(expected.getUnitPrice());
            assertThat(result.tierAdjustedTotal())
                    .isEqualByComparingTo(expected.getUnitPrice().multiply(BigDecimal.valueOf(quantity)));
        }
    }

    static Stream<Arguments> bulkTierCases() {
        BulkPricingTier t5 = tier(5, "90.00");
        BulkPricingTier t10 = tier(10, "80.00");
        List<BulkPricingTier> tiers = List.of(t5, t10);
        return Stream.of(
                Arguments.of("no tiers configured", List.of(), 3, null),
                Arguments.of("below smallest tier", tiers, 4, null),
                Arguments.of("exact boundary match", tiers, 5, 0),
                Arguments.of("above boundary, below next tier", tiers, 7, 0),
                Arguments.of("multiple tiers, closest-below wins", tiers, 12, 1)
        );
    }

    // ---- applyContractOverride ----
    // Expiry/not-yet-effective resolution happens in ContractPriceRepositoryAdapter.findActive
    // (see ContractPriceRepositoryAdapterTest) — by the time PricingContext reaches this step,
    // activeContractPrice is already either "the currently active one" or null. So this step
    // only has one real branch to test: present vs. absent.

    @Test
    void applyContractOverride_present_overridesTierTotal() {
        ContractPrice contract = new ContractPrice(UUID.randomUUID(), UUID.randomUUID(), PRODUCT_ID,
                new BigDecimal("70.00"), NOW, null, NOW, NOW);
        PricingContext ctx = new ContextBuilder().contractPrice(contract).build();

        PriceCalculationResult running = PricingSteps.applyBulkTier(initial(3), new ContextBuilder().build());
        PriceCalculationResult result = PricingSteps.applyContractOverride(running, ctx);

        assertThat(result.appliedContractId()).isEqualTo(contract.getId());
        assertThat(result.contractAdjustedTotal()).isEqualByComparingTo(new BigDecimal("210.00"));
    }

    @Test
    void applyContractOverride_absent_passesThroughTierTotal() {
        PricingContext ctx = new ContextBuilder().build();
        PriceCalculationResult running = PricingSteps.applyBulkTier(initial(3), ctx);

        PriceCalculationResult result = PricingSteps.applyContractOverride(running, ctx);

        assertThat(result.appliedContractId()).isNull();
        assertThat(result.contractAdjustedTotal()).isEqualByComparingTo(result.tierAdjustedTotal());
    }

    // ---- applyCoupon ----

    private static PriceCalculationResult afterContract(BigDecimal contractAdjustedTotal) {
        PriceCalculationResult running = initial(1);
        running = PricingSteps.applyBulkTier(running, new ContextBuilder().build());
        return PricingSteps.applyContractOverride(running, new ContextBuilder()
                .contractPrice(new ContractPrice(UUID.randomUUID(), UUID.randomUUID(), PRODUCT_ID,
                        contractAdjustedTotal, NOW, null, NOW, NOW))
                .build());
    }

    private static Coupon coupon(DiscountType type, String value, Instant expiresAt, Integer maxUsesPerUser,
                                  List<UUID> eligibleCategoryIds, boolean active) {
        return new Coupon(UUID.randomUUID(), "SAVE10", type, new BigDecimal(value), null, expiresAt, maxUsesPerUser,
                eligibleCategoryIds, false, active, NOW, NOW);
    }

    @Test
    void applyCoupon_noCodeRequested_passesThrough() {
        PriceCalculationResult running = afterContract(new BigDecimal("200.00"));
        PriceCalculationResult result = PricingSteps.applyCoupon(running, new ContextBuilder().build());

        assertThat(result.appliedCouponId()).isNull();
        assertThat(result.couponDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // Invalid coupons skip (discount ZERO, no appliedCouponId) instead of throwing —
    // a throw here poisons the whole cart: GET /cart 404s and the line can't be removed.

    @Test
    void applyCoupon_codeNotFound_skipsCoupon() {
        PriceCalculationResult running = afterContract(new BigDecimal("200.00"));
        PricingContext ctx = new ContextBuilder().coupon("BADCODE", null, 0).build();

        PriceCalculationResult result = PricingSteps.applyCoupon(running, ctx);

        assertThat(result.appliedCouponId()).isNull();
        assertThat(result.couponDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void applyCoupon_inactive_skipsCoupon() {
        PriceCalculationResult running = afterContract(new BigDecimal("200.00"));
        Coupon inactive = coupon(DiscountType.FLAT, "10", NOW.plusSeconds(3600), null, List.of(), false);
        PricingContext ctx = new ContextBuilder().coupon("SAVE10", inactive, 0).build();

        PriceCalculationResult result = PricingSteps.applyCoupon(running, ctx);

        assertThat(result.appliedCouponId()).isNull();
        assertThat(result.couponDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void applyCoupon_expired_skipsCoupon() {
        PriceCalculationResult running = afterContract(new BigDecimal("200.00"));
        Coupon expired = coupon(DiscountType.FLAT, "10", NOW.minusSeconds(1), null, List.of(), true);
        PricingContext ctx = new ContextBuilder().coupon("SAVE10", expired, 0).build();

        PriceCalculationResult result = PricingSteps.applyCoupon(running, ctx);

        assertThat(result.appliedCouponId()).isNull();
        assertThat(result.couponDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void applyCoupon_usageLimitReached_skipsCoupon() {
        PriceCalculationResult running = afterContract(new BigDecimal("200.00"));
        Coupon limited = coupon(DiscountType.FLAT, "10", NOW.plusSeconds(3600), 2, List.of(), true);
        PricingContext ctx = new ContextBuilder().coupon("SAVE10", limited, 2).build();

        PriceCalculationResult result = PricingSteps.applyCoupon(running, ctx);

        assertThat(result.appliedCouponId()).isNull();
        assertThat(result.couponDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void applyCoupon_categoryIneligible_skipsCoupon() {
        PriceCalculationResult running = afterContract(new BigDecimal("200.00"));
        Coupon ineligible = coupon(DiscountType.FLAT, "10", NOW.plusSeconds(3600), null,
                List.of(UUID.randomUUID()), true);
        PricingContext ctx = new ContextBuilder().coupon("SAVE10", ineligible, 0).build();

        PriceCalculationResult result = PricingSteps.applyCoupon(running, ctx);

        assertThat(result.appliedCouponId()).isNull();
        assertThat(result.couponDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void applyCoupon_minOrderValueNotMet_skipsCoupon() {
        PriceCalculationResult running = afterContract(new BigDecimal("200.00"));
        Coupon minOrder = new Coupon(UUID.randomUUID(), "SAVE10", DiscountType.FLAT, new BigDecimal("10"),
                new BigDecimal("500.00"), NOW.plusSeconds(3600), null, List.of(), false, true, NOW, NOW);
        PricingContext ctx = new ContextBuilder().coupon("SAVE10", minOrder, 0).build();

        PriceCalculationResult result = PricingSteps.applyCoupon(running, ctx);

        assertThat(result.appliedCouponId()).isNull();
        assertThat(result.couponDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void applyCoupon_minOrderValueMet_appliesCoupon() {
        PriceCalculationResult running = afterContract(new BigDecimal("600.00"));
        Coupon minOrder = new Coupon(UUID.randomUUID(), "SAVE10", DiscountType.FLAT, new BigDecimal("10"),
                new BigDecimal("500.00"), NOW.plusSeconds(3600), null, List.of(), false, true, NOW, NOW);
        PricingContext ctx = new ContextBuilder().coupon("SAVE10", minOrder, 0).build();

        PriceCalculationResult result = PricingSteps.applyCoupon(running, ctx);

        assertThat(result.appliedCouponId()).isEqualTo(minOrder.getId());
        assertThat(result.couponDiscountAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void applyCoupon_percentDiscount_computesAmount() {
        PriceCalculationResult running = afterContract(new BigDecimal("200.00"));
        Coupon percentOff = coupon(DiscountType.PERCENT, "10", NOW.plusSeconds(3600), null, List.of(), true);
        PricingContext ctx = new ContextBuilder().coupon("SAVE10", percentOff, 0).build();

        PriceCalculationResult result = PricingSteps.applyCoupon(running, ctx);

        assertThat(result.appliedCouponId()).isEqualTo(percentOff.getId());
        assertThat(result.couponDiscountAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    void applyCoupon_flatDiscount_cappedAtTotal() {
        PriceCalculationResult running = afterContract(new BigDecimal("15.00"));
        Coupon flatOff = coupon(DiscountType.FLAT, "50", NOW.plusSeconds(3600), null, List.of(), true);
        PricingContext ctx = new ContextBuilder().coupon("SAVE10", flatOff, 0).build();

        PriceCalculationResult result = PricingSteps.applyCoupon(running, ctx);

        assertThat(result.couponDiscountAmount()).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    // ---- applyMarginFloor ----
    // Whether marginRule came from a product-level or category-level lookup is decided in
    // PricingCalculatorImpl.loadContext (see PricingCalculatorImplTest) — this step only ever
    // sees the already-resolved MarginRule (or null) and can't distinguish its origin.

    private static PriceCalculationResult afterCoupon(BigDecimal contractAdjustedTotal, BigDecimal couponDiscount) {
        return withCouponDiscount(afterContract(contractAdjustedTotal), couponDiscount);
    }

    private static PriceCalculationResult withCouponDiscount(PriceCalculationResult running, BigDecimal discount) {
        Coupon coupon = coupon(DiscountType.FLAT, discount.toPlainString(), NOW.plusSeconds(3600), null, List.of(), true);
        return PricingSteps.applyCoupon(running, new ContextBuilder().coupon("X", coupon, 0).build());
    }

    @Test
    void applyMarginFloor_noRule_passesThroughSilently() {
        PriceCalculationResult running = afterCoupon(new BigDecimal("50.00"), BigDecimal.ZERO);
        PriceCalculationResult result = PricingSteps.applyMarginFloor(running, new ContextBuilder().build());

        assertThat(result.marginFloorTriggered()).isFalse();
        assertThat(result.appliedMarginRuleId()).isNull();
        assertThat(result.subtotal()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void applyMarginFloor_explicitFloorPrice_triggersWhenBelow() {
        PriceCalculationResult running = afterCoupon(new BigDecimal("50.00"), BigDecimal.ZERO);
        MarginRule rule = new MarginRule(UUID.randomUUID(), PRODUCT_ID, null, null, null, new BigDecimal("60.00"), NOW, NOW);

        PriceCalculationResult result = PricingSteps.applyMarginFloor(running, new ContextBuilder().marginRule(rule).build());

        assertThat(result.marginFloorTriggered()).isTrue();
        assertThat(result.marginFloorAdjustment()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(result.subtotal()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(result.appliedMarginRuleId()).isEqualTo(rule.getId());
    }

    @Test
    void applyMarginFloor_explicitFloorPrice_notTriggeredWhenAbove() {
        PriceCalculationResult running = afterCoupon(new BigDecimal("100.00"), BigDecimal.ZERO);
        MarginRule rule = new MarginRule(UUID.randomUUID(), PRODUCT_ID, null, null, null, new BigDecimal("60.00"), NOW, NOW);

        PriceCalculationResult result = PricingSteps.applyMarginFloor(running, new ContextBuilder().marginRule(rule).build());

        assertThat(result.marginFloorTriggered()).isFalse();
        assertThat(result.marginFloorAdjustment()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.subtotal()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void applyMarginFloor_costPricePlusFloorPercent_triggersWhenBelow() {
        PriceCalculationResult running = afterCoupon(new BigDecimal("50.00"), BigDecimal.ZERO);
        // floor = 50 cost * (1 + 20%) = 60
        MarginRule rule = new MarginRule(UUID.randomUUID(), PRODUCT_ID, null,
                new BigDecimal("50.00"), new BigDecimal("20"), null, NOW, NOW);

        PriceCalculationResult result = PricingSteps.applyMarginFloor(running, new ContextBuilder().marginRule(rule).build());

        assertThat(result.marginFloorTriggered()).isTrue();
        assertThat(result.subtotal()).isEqualByComparingTo(new BigDecimal("60.0000"));
    }

    @Test
    void applyMarginFloor_malformedRule_passesThroughWithoutThrowing() {
        PriceCalculationResult running = afterCoupon(new BigDecimal("50.00"), BigDecimal.ZERO);
        MarginRule malformed = new MarginRule(UUID.randomUUID(), PRODUCT_ID, null, null, null, null, NOW, NOW);

        PriceCalculationResult result = PricingSteps.applyMarginFloor(running, new ContextBuilder().marginRule(malformed).build());

        assertThat(result.marginFloorTriggered()).isFalse();
        assertThat(result.subtotal()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void applyMarginFloor_couponDiscountCanPushBelowFloor() {
        // 100 contract total, 60 coupon discount -> 40 pre-floor, floor is 55 -> triggered
        PriceCalculationResult running = withCouponDiscount(afterContract(new BigDecimal("100.00")), new BigDecimal("60.00"));
        MarginRule rule = new MarginRule(UUID.randomUUID(), PRODUCT_ID, null, null, null, new BigDecimal("55.00"), NOW, NOW);

        PriceCalculationResult result = PricingSteps.applyMarginFloor(running, new ContextBuilder().marginRule(rule).build());

        assertThat(result.marginFloorTriggered()).isTrue();
        assertThat(result.subtotal()).isEqualByComparingTo(new BigDecimal("55.00"));
    }

    // ---- applyGst ----

    @Test
    void applyGst_resolved_computesAmountAndFinalPrice() {
        PriceCalculationResult running = PricingSteps.applyMarginFloor(
                afterCoupon(new BigDecimal("100.00"), BigDecimal.ZERO), new ContextBuilder().build());

        PriceCalculationResult result = PricingSteps.applyGst(running, new ContextBuilder()
                .gstRatePercent(new BigDecimal("18")).build());

        assertThat(result.gstAmount()).isEqualByComparingTo(new BigDecimal("18.00"));
        assertThat(result.finalPrice()).isEqualByComparingTo(new BigDecimal("118.00"));
    }

    @Test
    void applyGst_unresolved_throwsWithHsnCode() {
        PriceCalculationResult running = PricingSteps.applyMarginFloor(
                afterCoupon(new BigDecimal("100.00"), BigDecimal.ZERO), new ContextBuilder().build());
        PricingContext ctx = new ContextBuilder().gstRatePercent(null).build();

        assertThatThrownBy(() -> PricingSteps.applyGst(running, ctx))
                .isInstanceOf(GstRateUnresolvedException.class)
                .extracting(ex -> ((GstRateUnresolvedException) ex).getHsnCode())
                .isEqualTo("1001");
    }

    private static final class ContextBuilder {
        private List<BulkPricingTier> tiers = List.of();
        private ContractPrice contractPrice;
        private String couponCode;
        private Coupon coupon;
        private int redemptions;
        private MarginRule marginRule;
        private BigDecimal gstRatePercent = new BigDecimal("18");

        ContextBuilder tiers(List<BulkPricingTier> v) {
            this.tiers = v;
            return this;
        }

        ContextBuilder contractPrice(ContractPrice v) {
            this.contractPrice = v;
            return this;
        }

        ContextBuilder coupon(String code, Coupon c, int redemptions) {
            this.couponCode = code;
            this.coupon = c;
            this.redemptions = redemptions;
            return this;
        }

        ContextBuilder marginRule(MarginRule v) {
            this.marginRule = v;
            return this;
        }

        ContextBuilder gstRatePercent(BigDecimal v) {
            this.gstRatePercent = v;
            return this;
        }

        PricingContext build() {
            return new PricingContext(product(), null, tiers, contractPrice, couponCode, coupon,
                    redemptions, marginRule, gstRatePercent, NOW);
        }
    }
}
