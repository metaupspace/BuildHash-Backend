package com.builddash.backend.application.impl;

import com.builddash.backend.application.event.ApprovalRequestedEvent;
import com.builddash.backend.application.service.ApprovalGateService;
import com.builddash.backend.domain.enums.ApprovalMatchRule;
import com.builddash.backend.domain.enums.ApprovalRequestStatus;
import com.builddash.backend.domain.model.ApprovalPolicy;
import com.builddash.backend.domain.model.ApprovalRequest;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.port.ApprovalPolicyRepository;
import com.builddash.backend.domain.port.ApprovalRequestRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.application.service.DeliverySlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApprovalGateServiceImpl implements ApprovalGateService {

    private final ApprovalPolicyRepository policyRepository;
    private final ApprovalRequestRepository requestRepository;
    private final ProductRepository productRepository;
    private final DeliverySlotService deliverySlotService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public GateDecision evaluate(UUID companyId, BigDecimal orderTotal, Collection<UUID> productIds, UUID siteId) {
        ApprovalPolicy policy = policyRepository.findByCompanyId(companyId).orElse(null);
        if (policy == null) {
            return GateDecision.notGated(); // no policy row = no gate (locked decision 8)
        }

        List<ApprovalMatchRule> rules = new ArrayList<>();
        List<UUID> matchedCategories = List.of();

        if (policy.amountThreshold() != null
                && orderTotal.compareTo(policy.amountThreshold()) >= 0) { // locked: >= not >
            rules.add(ApprovalMatchRule.AMOUNT);
        }

        if (!policy.categoryIds().isEmpty()) {
            Set<UUID> lineCategories = new HashSet<>();
            for (UUID productId : productIds) {
                productRepository.findById(productId)
                        .map(Product::getCategoryId)
                        .ifPresent(lineCategories::add);
            }
            matchedCategories = policy.categoryIds().stream()
                    .filter(lineCategories::contains)
                    .toList();
            if (!matchedCategories.isEmpty()) {
                rules.add(ApprovalMatchRule.CATEGORY);
            }
        }

        // H0.5: fail closed — a site-constrained policy never lets a null-site order
        // pass ungated (unreachable from checkout since siteId became mandatory, but
        // this is the belt for any other caller of evaluate()).
        if (!policy.siteIds().isEmpty()
                && (siteId == null || policy.siteIds().contains(siteId))) {
            rules.add(ApprovalMatchRule.SITE);
        }

        if (rules.isEmpty()) {
            return GateDecision.notGated();
        }
        return new GateDecision(true, List.copyOf(rules), matchedCategories, policy.amountThreshold(),
                policy.roleStages(), policy.escalationHours(), policy.version());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ApprovalRequest openApproval(Order order, GateDecision decision, UUID deliverySlotLockId) {
        if (!decision.gated()) {
            throw new IllegalArgumentException("openApproval called with a non-gated decision");
        }
        // Capacity must not be held while approval pends (locked decision): release the
        // slot the checkout just acquired. The gated Order row carries a null lock id.
        deliverySlotService.releaseLock(deliverySlotLockId, order.userId());

        ApprovalRequest request = new ApprovalRequest(
                UUID.randomUUID(), order.id(), order.companyId(), ApprovalRequestStatus.PENDING,
                0, decision.roleStages().get(0), null,
                Instant.now().plus(Duration.ofHours(decision.escalationHours())),
                order.totalAmount(), decision.matchedRules(), decision.thresholdAmount(),
                decision.matchedCategoryIds(), order.siteId(), decision.roleStages(),
                decision.escalationHours(), decision.policyVersion(), null, null);
        ApprovalRequest saved = requestRepository.save(request);

        eventPublisher.publishEvent(new ApprovalRequestedEvent(order.id(), saved.id(),
                order.companyId(), saved.currentRole(), order.siteId(), order.userId()));
        return saved;
    }
}
