package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.CompanyStatus;
import com.builddash.backend.domain.model.CompanyContractPrice;
import com.builddash.backend.domain.model.ContractPrice;
import com.builddash.backend.domain.model.PriceCalculationResult;
import com.builddash.backend.domain.model.PricingRequest;
import com.builddash.backend.domain.model.Product;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Decision 7 regression trio: company contract beats user contract; user contract still
 * applies alone; and a null companyId (every B2C request) never touches the company
 * tier at all — the B2C path is provably unchanged.
 */
@ExtendWith(MockitoExtension.class)
class PricingCompanyPrecedenceTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private HsnGstRateRepository hsnGstRateRepository;
    @Mock private ProductBasePriceRepository productBasePriceRepository;
    @Mock private BulkPricingTierRepository bulkPricingTierRepository;
    @Mock private ContractPriceRepository contractPriceRepository;
    @Mock private CompanyContractPriceRepository companyContractPriceRepository;
    @Mock private CouponRepository couponRepository;
    @Mock private CouponRedemptionRepository couponRedemptionRepository;
    @Mock private MarginRuleRepository marginRuleRepository;

    private PricingCalculatorImpl calculator;

    private final UUID productId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID userContractId = UUID.randomUUID();
    private final UUID companyContractId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        calculator = new PricingCalculatorImpl(productRepository, categoryRepository,
                hsnGstRateRepository, productBasePriceRepository, bulkPricingTierRepository,
                contractPriceRepository, companyContractPriceRepository, couponRepository,
                couponRedemptionRepository, marginRuleRepository);

        Product product = new Product();
        product.setId(productId);
        product.setHsnCode("2523");
        lenient().when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        lenient().when(productBasePriceRepository.findByProductId(productId))
                .thenReturn(Optional.of(new BigDecimal("400.00")));
        lenient().when(bulkPricingTierRepository.findByProductId(productId)).thenReturn(List.of());
        com.builddash.backend.domain.model.HsnGstRate gst = new com.builddash.backend.domain.model.HsnGstRate();
        gst.setGstRatePercent(new BigDecimal("18.00"));
        lenient().when(hsnGstRateRepository.findByHsnCode("2523")).thenReturn(Optional.of(gst));
        lenient().when(marginRuleRepository.findByProductId(productId)).thenReturn(Optional.empty());
    }

    private ContractPrice userContract(String price) {
        return new ContractPrice(userContractId, userId, productId, new BigDecimal(price),
                Instant.now().minusSeconds(3600), null, null, null);
    }

    private CompanyContractPrice companyContract(String price) {
        return new CompanyContractPrice(companyContractId, companyId, productId, new BigDecimal(price),
                Instant.now().minusSeconds(3600), null, null, null);
    }

    @Test
    void companyContract_overridesUserContract() {
        when(companyContractPriceRepository.findActive(eq(companyId), eq(productId), any()))
                .thenReturn(Optional.of(companyContract("250.00")));

        PriceCalculationResult result = calculator.calculate(
                new PricingRequest(productId, 2, userId, null, companyId));

        // Company hit: the user tier is never even consulted (short-circuit precedence)
        verify(contractPriceRepository, never()).findActive(any(), any(), any());
        assertThat(result.appliedContractId()).isEqualTo(companyContractId);
        assertThat(result.contractUnitPrice()).isEqualByComparingTo("250.00");
        assertThat(result.finalPrice()).isEqualByComparingTo("590.00"); // 250*2 + 18% GST
    }

    @Test
    void userContract_appliesWhenNoCompanyContractExists() {
        when(companyContractPriceRepository.findActive(eq(companyId), eq(productId), any()))
                .thenReturn(Optional.empty());
        when(contractPriceRepository.findActive(eq(userId), eq(productId), any()))
                .thenReturn(Optional.of(userContract("300.00")));

        PriceCalculationResult result = calculator.calculate(
                new PricingRequest(productId, 2, userId, null, companyId));

        assertThat(result.appliedContractId()).isEqualTo(userContractId);
        assertThat(result.contractUnitPrice()).isEqualByComparingTo("300.00");
    }

    @Test
    void nullCompanyId_b2cPath_neverTouchesCompanyTier() {
        when(contractPriceRepository.findActive(eq(userId), eq(productId), any()))
                .thenReturn(Optional.of(userContract("300.00")));

        PriceCalculationResult result = calculator.calculate(
                new PricingRequest(productId, 2, userId, null, null));

        verify(companyContractPriceRepository, never()).findActive(any(), any(), any());
        assertThat(result.appliedContractId()).isEqualTo(userContractId);
        assertThat(result.finalPrice()).isEqualByComparingTo("708.00"); // 300*2 + 18% GST
    }
}
