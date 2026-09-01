package com.builddash.backend.application.impl;

import com.builddash.backend.application.event.ApprovalEscalatedEvent;
import com.builddash.backend.application.service.ApprovalEligibilityResolver;
import com.builddash.backend.application.service.ApprovalEscalationService;
import com.builddash.backend.domain.enums.ApprovalActionType;
import com.builddash.backend.domain.enums.ApprovalRequestStatus;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.model.ApprovalAction;
import com.builddash.backend.domain.model.ApprovalRequest;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.ApprovalActionRepository;
import com.builddash.backend.domain.port.ApprovalRequestRepository;
import com.builddash.backend.domain.port.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Escalation sweep (9-D). No automatic approval or cancellation — PENDING may persist
 * indefinitely; the sweep only advances stages.
 *
 * Multi-instance safety (StaleOrderSweep pattern): due ids are read unlocked, then each
 * request is processed in its own REQUIRES_NEW transaction under findByIdForUpdate with
 * a full re-check, so a second instance simply observes the advanced dueAt / terminal
 * status and skips. The UNIQUE(request_id, action_type, stage_index) backstop makes
 * duplicate stage actions impossible regardless.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalEscalationServiceImpl implements ApprovalEscalationService {

    private final ApprovalRequestRepository requestRepository;
    private final ApprovalActionRepository actionRepository;
    private final OrderRepository orderRepository;
    private final ApprovalEligibilityResolver eligibilityResolver;
    private final ApplicationEventPublisher eventPublisher;

    @Lazy
    @Autowired
    private ApprovalEscalationServiceImpl self;

    @Override
    public int escalateDue() {
        int processed = 0;
        for (UUID requestId : requestRepository.findDueIds(Instant.now())) {
            try {
                if (self.escalateOne(requestId)) {
                    processed++;
                }
            } catch (Exception e) {
                // One bad request must not kill the batch — per-request isolation (locked).
                log.warn("Approval escalation failed for request {}: {}", requestId, e.getMessage());
            }
        }
        return processed;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean escalateOne(UUID requestId) {
        ApprovalRequest request = requestRepository.findByIdForUpdate(requestId).orElse(null);
        if (request == null || request.status() != ApprovalRequestStatus.PENDING) {
            return false;
        }
        Instant now = Instant.now();
        if (request.escalationDueAt() == null || request.escalationDueAt().isAfter(now)) {
            return false; // raced: another instance advanced or blocked it first
        }
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new IllegalStateException(
                        "Approval request " + requestId + " references missing order " + request.orderId()));

        for (int stage = request.currentStageIndex() + 1; stage < request.roleStages().size(); stage++) {
            CompanyRole role = request.roleStages().get(stage);
            // A configured role alone is insufficient: the stage must have a currently
            // eligible member (membership, APPROVAL_ACT/OWNER, site scope, not placer).
            if (!eligibilityResolver.hasEligibleApprover(request.companyId(), role, request.siteId(), order.userId())) {
                continue;
            }
            ApprovalRequest escalated = requestRepository.save(request.escalateTo(stage,
                    now.plus(Duration.ofHours(request.escalationHours()))));
            actionRepository.save(new ApprovalAction(UUID.randomUUID(), request.id(),
                    ApprovalActionType.ESCALATED, null, null, stage, null, null));
            eventPublisher.publishEvent(new ApprovalEscalatedEvent(order.id(), request.id(),
                    request.companyId(), stage, escalated.currentRole(), request.siteId(), order.userId()));
            return true;
        }

        // No later stage with an eligible member: block exactly once and stop mutating.
        // dueAt null removes the request from the due query forever; still PENDING.
        if (!actionRepository.existsByRequestIdAndTypeAndStageIndex(
                request.id(), ApprovalActionType.ESCALATION_BLOCKED, request.currentStageIndex())) {
            actionRepository.save(new ApprovalAction(UUID.randomUUID(), request.id(),
                    ApprovalActionType.ESCALATION_BLOCKED, null, null,
                    request.currentStageIndex(), null, null));
        }
        requestRepository.save(request.blockEscalation());
        return true;
    }
}
