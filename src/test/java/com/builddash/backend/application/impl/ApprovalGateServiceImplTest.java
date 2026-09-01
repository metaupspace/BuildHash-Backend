package com.builddash.backend.application.impl;

import com.builddash.backend.application.event.ApprovalRequestedEvent;
import com.builddash.backend.application.service.ApprovalGateService;
import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.domain.enums.ApprovalMatchRule;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.model.ApprovalPolicy;
import com.builddash.backend.domain.model.ApprovalRequest;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.port.ApprovalPolicyRepository;
import com.builddash.backend.domain.port.ApprovalRequestRepository;
import com.builddash.backend.domain.port.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ApprovalGateServiceImplTest {

    @Mock
    private ApprovalPolicyRepository policyRepository;
    @Mock
    private ApprovalRequestRepository requestRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private DeliverySlotService deliverySlotService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ApprovalGateServiceImpl gateService;

    private final UUID companyId = UUID.randomUUID();
    private final UUID amountCategory = UUID.randomUUID();
    private final UUID otherCategory = UUID.randomUUID();
    private final UUID siteId = UUID.randomUUID();
    private final UUID otherSiteId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    private ApprovalPolicy policy(BigDecimal threshold, List<UUID> categories, List<UUID> sites) {
        return new ApprovalPolicy(UUID.randomUUID(), companyId, threshold, categories, sites,
                List.of(CompanyRole.PROCUREMENT_MANAGER, CompanyRole.OWNER), 24, 3, null, null);
    }

    private Product product(UUID categoryId) {
        Product p = new Product();
        p.setCategoryId(categoryId);
        return p;
    }

    private ApprovalGateService.GateDecision evaluate(BigDecimal threshold, List<UUID> categories,
                                                      List<UUID> sites, BigDecimal total, UUID orderSite) {
        when(policyRepository.findByCompanyId(companyId)).thenReturn(Optional.of(policy(threshold, categories, sites)));
        lenient().when(productRepository.findById(productId)).thenReturn(Optional.of(product(amountCategory)));
        return gateService.evaluate(companyId, total, List.of(productId), orderSite);
    }

    @Test
    void evaluate_noPolicyRow_notGated() {
        when(policyRepository.findByCompanyId(companyId)).thenReturn(Optional.empty());
        assertThat(gateService.evaluate(companyId, new BigDecimal("999.00"), List.of(), siteId).gated()).isFalse();
    }

    @Test
    void evaluate_amountOnly_gates() {
        var d = evaluate(new BigDecimal("100.00"), List.of(), List.of(), new BigDecimal("150.00"), null);
        assertThat(d.gated()).isTrue();
        assertThat(d.matchedRules()).containsExactly(ApprovalMatchRule.AMOUNT);
        assertThat(d.matchedCategoryIds()).isEmpty();
    }

    @Test
    void evaluate_amountEqualToThreshold_gates_inclusiveComparison() {
        var d = evaluate(new BigDecimal("150.00"), List.of(), List.of(), new BigDecimal("150.00"), null);
        assertThat(d.gated()).isTrue();
        assertThat(d.matchedRules()).containsExactly(ApprovalMatchRule.AMOUNT);
    }

    @Test
    void evaluate_amountBelowThreshold_aloneDoesNotGate() {
        assertThat(evaluate(new BigDecimal("150.01"), List.of(), List.of(), new BigDecimal("150.00"), null).gated()).isFalse();
    }

    @Test
    void evaluate_categoryOnly_gates_andSnapshotsMatchedCategory() {
        var d = evaluate(null, List.of(amountCategory), List.of(), new BigDecimal("1.00"), null);
        assertThat(d.gated()).isTrue();
        assertThat(d.matchedRules()).containsExactly(ApprovalMatchRule.CATEGORY);
        assertThat(d.matchedCategoryIds()).containsExactly(amountCategory);
    }

    @Test
    void evaluate_categoryConfiguredButNoLineMatch_notGated() {
        assertThat(evaluate(null, List.of(otherCategory), List.of(), new BigDecimal("1.00"), null).gated()).isFalse();
    }

    @Test
    void evaluate_siteOnly_gates() {
        var d = evaluate(null, List.of(), List.of(siteId), new BigDecimal("1.00"), siteId);
        assertThat(d.gated()).isTrue();
        assertThat(d.matchedRules()).containsExactly(ApprovalMatchRule.SITE);
    }

    @Test
    void evaluate_otherSite_notGated_andNullSiteNeverMatches() {
        assertThat(evaluate(null, List.of(), List.of(siteId), new BigDecimal("1.00"), otherSiteId).gated()).isFalse();
        assertThat(evaluate(null, List.of(), List.of(siteId), new BigDecimal("1.00"), null).gated()).isFalse();
    }

    @Test
    void evaluate_amountPlusCategory_bothRules() {
        var d = evaluate(new BigDecimal("100.00"), List.of(amountCategory), List.of(),
                new BigDecimal("150.00"), null);
        assertThat(d.matchedRules()).containsExactlyInAnyOrder(ApprovalMatchRule.AMOUNT, ApprovalMatchRule.CATEGORY);
    }

    @Test
    void evaluate_categoryPlusSite_bothRules() {
        var d = evaluate(null, List.of(amountCategory), List.of(siteId), new BigDecimal("1.00"), siteId);
        assertThat(d.matchedRules()).containsExactlyInAnyOrder(ApprovalMatchRule.CATEGORY, ApprovalMatchRule.SITE);
    }

    @Test
    void evaluate_allThreeConditions() {
        var d = evaluate(new BigDecimal("100.00"), List.of(amountCategory), List.of(siteId),
                new BigDecimal("150.00"), siteId);
        assertThat(d.matchedRules()).containsExactlyInAnyOrder(
                ApprovalMatchRule.AMOUNT, ApprovalMatchRule.CATEGORY, ApprovalMatchRule.SITE);
    }

    @Test
    void evaluate_snapshotCarriesPolicyConfiguration() {
        var d = evaluate(new BigDecimal("100.00"), List.of(), List.of(), new BigDecimal("150.00"), null);
        assertThat(d.thresholdAmount()).isEqualByComparingTo("100.00");
        assertThat(d.roleStages()).containsExactly(CompanyRole.PROCUREMENT_MANAGER, CompanyRole.OWNER);
        assertThat(d.escalationHours()).isEqualTo(24);
        assertThat(d.policyVersion()).isEqualTo(3);
    }

    @Test
    void openApproval_releasesSlot_snapshotsRequest_publishesEvent() {
        var decision = new ApprovalGateService.GateDecision(true, List.of(ApprovalMatchRule.AMOUNT),
                List.of(), new BigDecimal("100.00"),
                List.of(CompanyRole.PROCUREMENT_MANAGER, CompanyRole.OWNER), 24, 3);
        UUID lockId = UUID.randomUUID();
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), new BigDecimal("150.00"),
                com.builddash.backend.domain.enums.OrderStatus.PENDING_APPROVAL, null,
                java.time.Instant.now(), null, null, List.of(), companyId, siteId, null);
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalRequest request = gateService.openApproval(order, decision, lockId);

        verify(deliverySlotService).releaseLock(lockId, order.userId());
        assertThat(request.orderId()).isEqualTo(order.id());
        assertThat(request.companyId()).isEqualTo(companyId);
        assertThat(request.status()).isEqualTo(com.builddash.backend.domain.enums.ApprovalRequestStatus.PENDING);
        assertThat(request.currentStageIndex()).isZero();
        assertThat(request.currentRole()).isEqualTo(CompanyRole.PROCUREMENT_MANAGER);
        assertThat(request.orderTotalAmount()).isEqualByComparingTo("150.00");
        assertThat(request.thresholdAmount()).isEqualByComparingTo("100.00");
        assertThat(request.siteId()).isEqualTo(siteId);
        assertThat(request.roleStages()).containsExactly(CompanyRole.PROCUREMENT_MANAGER, CompanyRole.OWNER);
        assertThat(request.escalationHours()).isEqualTo(24);
        assertThat(request.policyVersion()).isEqualTo(3);
        assertThat(request.escalationDueAt()).isAfter(java.time.Instant.now());

        ArgumentCaptor<ApprovalRequestedEvent> captor = ArgumentCaptor.forClass(ApprovalRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo(order.id());
        assertThat(captor.getValue().placerUserId()).isEqualTo(order.userId());
    }
}
