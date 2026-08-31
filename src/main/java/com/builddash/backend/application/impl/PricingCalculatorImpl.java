package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.PricingCalculator;
import com.builddash.backend.application.service.PricingStep;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.ProductNotPricedException;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Coupon;
import com.builddash.backend.domain.model.HsnGstRate;
import com.builddash.backend.domain.model.MarginRule;
import com.builddash.backend.domain.model.PriceCalculationResult;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.PricingRequest;
import com.builddash.backend.domain.model.ResolvedContract;
import com.builddash.backend.domain.port.BulkPricingTierRepository;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.CompanyContractPriceRepository;
import com.builddash.backend.domain.port.ContractPriceRepository;
import com.builddash.backend.domain.port.CouponRedemptionRepository;
import com.builddash.backend.domain.port.CouponRepository;
import com.builddash.backend.domain.port.HsnGstRateRepository;
import com.builddash.backend.domain.port.MarginRuleRepository;
import com.builddash.backend.domain.port.ProductBasePriceRepository;
import com.builddash.backend.domain.port.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class PricingCalculatorImpl implements PricingCalculator {

    private static final List<PricingStep> PIPELINE = List.of(
            PricingSteps::applyBulkTier,
            PricingSteps::applyContractOverride,
            PricingSteps::applyCoupon,
            PricingSteps::applyMarginFloor,
            PricingSteps::applyGst
    );

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final HsnGstRateRepository hsnGstRateRepository;
    private final ProductBasePriceRepository productBasePriceRepository;
    private final BulkPricingTierRepository bulkPricingTierRepository;
    private final ContractPriceRepository contractPriceRepository;
    private final CompanyContractPriceRepository companyContractPriceRepository;
    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final MarginRuleRepository marginRuleRepository;



    @Override
    public PriceCalculationResult calculate(PricingRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found: " + request.productId()));
        BigDecimal basePrice = productBasePriceRepository.findByProductId(request.productId())
                .orElseThrow(() -> new ProductNotPricedException(request.productId()));

        PricingContext ctx = loadContext(request, product);

        PriceCalculationResult result = PriceCalculationResult.initial(request, product.getHsnCode(), basePrice);
        for (PricingStep step : PIPELINE) {
            result = step.apply(result, ctx);
        }
        return result;
    }

    private PricingContext loadContext(PricingRequest request, Product product) {
        Category category = categoryRepository.findById(product.getCategoryId()).orElse(null);

        Coupon coupon = request.couponCode() == null
                ? null
                : couponRepository.findByCode(request.couponCode()).orElse(null);
        int redemptionCount = (coupon != null && request.userId() != null)
                ? couponRedemptionRepository.countByUserAndCoupon(request.userId(), coupon.getId())
                : 0;

        Instant asOf = Instant.now();
        // Contract resolution — the ONE place the company->user precedence exists
        // (decision 7). Steps receive the single winner as a ResolvedContract and stay
        // ignorant of tiers. companyId == null (every B2C request) skips the company
        // lookup entirely: identical behavior to pre-9A pricing.
        Optional<ResolvedContract> resolvedContract = Optional.empty();
        if (request.companyId() != null) {
            resolvedContract = companyContractPriceRepository
                    .findActive(request.companyId(), request.productId(), asOf)
                    .map(cp -> new ResolvedContract(cp.id(), cp.unitPrice()));
        }
        if (resolvedContract.isEmpty() && request.userId() != null) {
            resolvedContract = contractPriceRepository
                    .findActive(request.userId(), request.productId(), asOf)
                    .map(cp -> new ResolvedContract(cp.getId(), cp.getUnitPrice()));
        }

        MarginRule marginRule = marginRuleRepository.findByProductId(request.productId())
                .or(() -> product.getCategoryId() == null
                        ? Optional.empty()
                        : marginRuleRepository.findByCategoryId(product.getCategoryId()))
                .orElse(null);

        BigDecimal gstRatePercent = hsnGstRateRepository.findByHsnCode(product.getHsnCode())
                .map(HsnGstRate::getGstRatePercent)
                .orElse(null);

        return new PricingContext(
                product,
                category,
                bulkPricingTierRepository.findByProductId(request.productId()),
                resolvedContract.orElse(null),
                request.couponCode(),
                coupon,
                redemptionCount,
                marginRule,
                gstRatePercent,
                asOf
        );
    }
}
