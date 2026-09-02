package com.builddash.backend.application.impl;

import com.builddash.backend.application.event.ApprovalDecidedEvent;
import com.builddash.backend.application.event.OrderCancelledEvent;
import com.builddash.backend.application.service.ApprovalEligibilityResolver;
import com.builddash.backend.application.service.ApprovalService;
import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.application.service.OrderService;
import com.builddash.backend.domain.enums.ApprovalActionType;
import com.builddash.backend.domain.enums.ApprovalRequestStatus;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.InvalidApprovalStateException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.SlotUnavailableException;
import com.builddash.backend.domain.model.ApprovalAction;
import com.builddash.backend.domain.model.ApprovalRequest;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.model.DeliverySlotLock;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.ApprovalActionRepository;
import com.builddash.backend.domain.port.ApprovalRequestRepository;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.domain.port.CompanySiteAssignmentRepository;
import com.builddash.backend.domain.port.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Approval decisions (9-D).
 *
 * Lock discipline (global order COMPANIES → ORDERS → APPROVAL_REQUESTS → SLOT_COUNTERS):
 * critical B2bAuthorizer always runs first inside the transaction, then the order row
 * lock, then the request row lock, then (approve only) the delivery counter. Delegate
 * skips the order lock — it mutates only the request and reads immutable order fields.
 *
 * Slot-failure cancellation must COMMIT, so approve() cannot throw inside the
 * transaction: the SlotUnavailableException is captured and rethrown after
 * TransactionTemplate.execute returns (OrderServiceImpl orchestration precedent).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalServiceImpl implements ApprovalService {

    /** Mirrors CheckoutIntentServiceImpl's intent TTL — the window the placer has to pay
     *  once the approval resumes PAYMENT_PENDING (sweep recovers it if they never do). */
    private static final Duration REACQUIRE_TTL = Duration.ofMinutes(15);

    private final B2bAuthorizer b2bAuthorizer;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalActionRepository approvalActionRepository;
    private final OrderRepository orderRepository;
    private final CompanyMemberRepository memberRepository;
    private final CompanySiteAssignmentRepository siteAssignmentRepository;
    private final ApprovalEligibilityResolver eligibilityResolver;
    private final DeliverySlotService deliverySlotService;
    private final OrderService orderService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalRequest> list(UUID userId, UUID companyId) {
        b2bAuthorizer.authorize(userId, companyId, CompanyPermission.APPROVAL_VIEW, null, false);
        CompanyMember member = requireMember(userId, companyId);
        List<UUID> assignedSites = siteAssignmentRepository.findSiteIdsByMemberId(member.id());
        return approvalRequestRepository.findByCompanyVisibleInSites(
                companyId, assignedSites.isEmpty() ? null : assignedSites);
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalDetail get(UUID userId, UUID approvalId) {
        ApprovalRequest request = load(approvalId);
        b2bAuthorizer.authorize(userId, request.companyId(), CompanyPermission.APPROVAL_VIEW,
                request.siteId(), false);
        // Mirror listing visibility: null-site requests are all-site members only.
        if (request.siteId() == null) {
            CompanyMember member = requireMember(userId, request.companyId());
            if (!siteAssignmentRepository.findSiteIdsByMemberId(member.id()).isEmpty()) {
                throw new NotFoundException("APPROVAL_NOT_FOUND", "Approval not found: " + approvalId);
            }
        }
        Order order = requireOrder(request.orderId());
        return new ApprovalDetail(request, order, approvalActionRepository.findByRequestId(approvalId));
    }

    @Override
    public ApprovalDetail approve(UUID actorUserId, UUID approvalId) {
        ApprovalRequest initial = load(approvalId);
        final ApprovalDetail result;
        try {
            result = transactionTemplate.execute(status -> {
                b2bAuthorizer.authorize(actorUserId, initial.companyId(), CompanyPermission.APPROVAL_ACT,
                        initial.siteId(), true);                                   // COMPANY
                Order order = orderRepository.findByIdForUpdate(initial.orderId()) // ORDER
                        .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND",
                                "Order not found: " + initial.orderId()));
                ApprovalRequest request = approvalRequestRepository.findByIdForUpdate(approvalId) // REQUEST
                        .orElseThrow(() -> new NotFoundException("APPROVAL_NOT_FOUND",
                                "Approval not found: " + approvalId));
                requirePending(request, order);
                CompanyMember actor = requireDecisionEligible(actorUserId, request, order);

                // H2.6: plain acquire, NOT acquireOrSwapLock. The gated order's own lock
                // was already released when approval opened, so there is no prior lock to
                // swap away from — and a swap here would release whatever OTHER active
                // lock the user happens to hold (a concurrent B2C checkout's), silently
                // freeing capacity that isn't ours.
                DeliverySlotLock lock = deliverySlotService.acquireLock(order.userId(),
                        order.slotId(), order.slotDate(), REACQUIRE_TTL);          // SLOT_COUNTER

                Order resumed = orderRepository.save(order.resumePayment(lock.id()));
                ApprovalRequest approved = approvalRequestRepository.save(request.approve());
                approvalActionRepository.save(new ApprovalAction(UUID.randomUUID(), approved.id(),
                        ApprovalActionType.APPROVED, actor.id(), null, approved.currentStageIndex(), null, null));
                eventPublisher.publishEvent(new ApprovalDecidedEvent(order.id(), approved.id(), true, order.userId()));
                return new ApprovalDetail(approved, resumed, List.of());
            });
        } catch (SlotUnavailableException e) {
            // acquireLock is a nested @Transactional join — its exception marks the
            // shared tx rollback-only, so the cancellation CANNOT ride on that transaction.
            // Cancel in a fresh one instead (sweep-precedent system path: order row lock,
            // request row lock, no company lock), re-checking state under those locks in
            // case a racing approver already resolved the request in the gap.
            cancelForSlotUnavailable(approvalId);
            throw e;
        }
        // Payment strictly post-commit (locked PAYMENT RESUME). A gateway failure here
        // leaves PAYMENT_PENDING with no PENDING payment; retryPayment is the recovery.
        orderService.initiatePaymentForApprovedOrder(result.order().id());
        return withActions(result.request().id(), result);
    }

    /** Second-chance cancellation after the approval tx rolled back on slot failure. */
    private void cancelForSlotUnavailable(UUID approvalId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                ApprovalRequest request = approvalRequestRepository.findByIdForUpdate(approvalId)
                        .orElseThrow(() -> new NotFoundException("APPROVAL_NOT_FOUND",
                                "Approval not found: " + approvalId));
                Order order = orderRepository.findByIdForUpdate(request.orderId())
                        .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND",
                                "Order not found: " + request.orderId()));
                if (request.status() != ApprovalRequestStatus.PENDING
                        || order.status() != OrderStatus.PENDING_APPROVAL) {
                    return; // a racing approver/placer already resolved it
                }
                approvalRequestRepository.save(request.cancel());
                approvalActionRepository.save(new ApprovalAction(UUID.randomUUID(), request.id(),
                        ApprovalActionType.CANCELLED, null, null, request.currentStageIndex(),
                        "APPROVAL_SLOT_UNAVAILABLE", null));
                orderRepository.save(order.cancelPendingApproval());
                eventPublisher.publishEvent(new OrderCancelledEvent(order.id(),
                        OrderCancelledEvent.OrderCancellationOrigin.APPROVAL_SLOT_UNAVAILABLE));
            });
        } catch (Exception e) {
            // The SlotUnavailableException for the caller matters more; the order stays
            // PENDING_APPROVAL and the next decision attempt retries the whole path.
            log.warn("Slot-failure cancellation failed for approval {}: {}", approvalId, e.getMessage());
        }
    }

    @Override
    public ApprovalDetail reject(UUID actorUserId, UUID approvalId) {
        ApprovalRequest initial = load(approvalId);
        ApprovalDetail result = transactionTemplate.execute(status -> {
            b2bAuthorizer.authorize(actorUserId, initial.companyId(), CompanyPermission.APPROVAL_ACT,
                    initial.siteId(), true);                                       // COMPANY
            Order order = orderRepository.findByIdForUpdate(initial.orderId())     // ORDER
                    .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND",
                            "Order not found: " + initial.orderId()));
            ApprovalRequest request = approvalRequestRepository.findByIdForUpdate(approvalId) // REQUEST
                    .orElseThrow(() -> new NotFoundException("APPROVAL_NOT_FOUND",
                            "Approval not found: " + approvalId));
            requirePending(request, order);
            CompanyMember actor = requireDecisionEligible(actorUserId, request, order);

            Order cancelled = orderRepository.save(order.cancelPendingApproval());
            ApprovalRequest rejected = approvalRequestRepository.save(request.reject());
            approvalActionRepository.save(new ApprovalAction(UUID.randomUUID(), rejected.id(),
                    ApprovalActionType.REJECTED, actor.id(), null, rejected.currentStageIndex(), null, null));
            eventPublisher.publishEvent(new OrderCancelledEvent(
                    order.id(), OrderCancelledEvent.OrderCancellationOrigin.APPROVAL_REJECTED));
            eventPublisher.publishEvent(new ApprovalDecidedEvent(order.id(), rejected.id(), false, order.userId()));
            return new ApprovalDetail(rejected, cancelled, List.of());
        });
        return withActions(result.request().id(), result);
    }

    @Override
    public ApprovalDetail delegate(UUID actorUserId, UUID approvalId, UUID delegateMemberId) {
        ApprovalRequest initial = load(approvalId);
        ApprovalDetail result = transactionTemplate.execute(status -> {
            b2bAuthorizer.authorize(actorUserId, initial.companyId(), CompanyPermission.APPROVAL_DELEGATE,
                    initial.siteId(), true);                                       // COMPANY
            ApprovalRequest request = approvalRequestRepository.findByIdForUpdate(approvalId) // REQUEST
                    .orElseThrow(() -> new NotFoundException("APPROVAL_NOT_FOUND",
                            "Approval not found: " + approvalId));
            if (request.status() != ApprovalRequestStatus.PENDING) {
                throw InvalidApprovalStateException.notPending(request.status());
            }
            if (request.assignedMemberId() != null) {
                throw InvalidApprovalStateException.alreadyDelegated();
            }
            // Plain read: placer userId and site are immutable on the order row.
            Order order = requireOrder(request.orderId());

            CompanyMember actor = requireMember(actorUserId, request.companyId());
            CompanyMember delegate = memberRepository.findById(delegateMemberId)
                    .filter(m -> m.companyId().equals(request.companyId()))
                    .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND",
                            "Delegate member not found in company " + request.companyId()));
            if (delegate.id().equals(actor.id())) {
                throw new ForbiddenException("DELEGATE_SELF", "Cannot delegate an approval to yourself");
            }
            if (delegate.userId().equals(order.userId())) {
                throw new ForbiddenException("SELF_APPROVAL_PROHIBITED",
                        "The order placer cannot become the approval delegate");
            }
            boolean delegateEligible = eligibilityResolver
                    .eligibleApprovers(request.companyId(), request.currentRole(), request.siteId(), order.userId())
                    .stream().anyMatch(m -> m.id().equals(delegate.id()));
            if (!delegateEligible) {
                throw new ForbiddenException("DELEGATE_INELIGIBLE",
                        "Delegate must match the current policy stage role, hold APPROVAL_ACT (or be OWNER), "
                                + "cover the site scope and not be the order placer");
            }

            ApprovalRequest assigned = approvalRequestRepository.save(request.assign(delegate.id()));
            approvalActionRepository.save(new ApprovalAction(UUID.randomUUID(), assigned.id(),
                    ApprovalActionType.DELEGATED, actor.id(), delegate.id(),
                    assigned.currentStageIndex(), null, null));
            return new ApprovalDetail(assigned, order, List.of());
        });
        return withActions(result.request().id(), result);
    }

    // ---------- shared helpers ----------

    private ApprovalRequest load(UUID approvalId) {
        return approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new NotFoundException("APPROVAL_NOT_FOUND",
                        "Approval not found: " + approvalId));
    }

    private Order requireOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found: " + orderId));
    }

    private CompanyMember requireMember(UUID userId, UUID companyId) {
        return memberRepository.findByCompanyIdAndUserId(companyId, userId)
                .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND",
                        "Company not found: " + companyId));
    }

    private void requirePending(ApprovalRequest request, Order order) {
        if (request.status() != ApprovalRequestStatus.PENDING) {
            throw InvalidApprovalStateException.notPending(request.status());
        }
        if (order.status() != OrderStatus.PENDING_APPROVAL) {
            throw InvalidApprovalStateException.orderNotPendingApproval(order.status().name());
        }
    }

    /**
     * Decision-time eligibility (locked APPROVER ELIGIBILITY): membership and APPROVAL_ACT
     * were proven by the authorizer; here — self-approval exclusion, current stage role,
     * assignment pin and (for null-site orders) the all-site requirement.
     */
    private CompanyMember requireDecisionEligible(UUID actorUserId, ApprovalRequest request, Order order) {
        CompanyMember member = requireMember(actorUserId, request.companyId());
        if (order.userId().equals(actorUserId)) {
            throw new ForbiddenException("SELF_APPROVAL_PROHIBITED",
                    "The order placer cannot approve or reject their own order");
        }
        boolean eligible = eligibilityResolver
                .eligibleApprovers(request.companyId(), request.currentRole(), request.siteId(), order.userId())
                .stream().anyMatch(m -> m.id().equals(member.id()));
        if (!eligible) {
            throw new ForbiddenException("APPROVAL_INELIGIBLE",
                    "Member does not match the current approval stage (role, APPROVAL_ACT, site scope)");
        }
        if (request.assignedMemberId() != null && !request.assignedMemberId().equals(member.id())) {
            throw new ForbiddenException("APPROVAL_DELEGATED_TO_OTHER",
                    "This approval is assigned to another member");
        }
        return member;
    }

    private ApprovalDetail withActions(UUID requestId, ApprovalDetail detail) {
        return new ApprovalDetail(detail.request(), detail.order(),
                approvalActionRepository.findByRequestId(requestId));
    }
}
