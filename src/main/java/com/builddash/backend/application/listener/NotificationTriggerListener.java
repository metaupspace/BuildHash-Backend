package com.builddash.backend.application.listener;

import com.builddash.backend.application.event.ApprovalDecidedEvent;
import com.builddash.backend.application.event.ApprovalEscalatedEvent;
import com.builddash.backend.application.event.ApprovalRequestedEvent;
import com.builddash.backend.application.event.InvoiceReadyEvent;
import com.builddash.backend.application.event.OrderCancelledEvent;
import com.builddash.backend.application.event.OrderDeliveredEvent;
import com.builddash.backend.application.event.OrderDispatchedEvent;
import com.builddash.backend.application.event.OrderPackedEvent;
import com.builddash.backend.application.event.RefundCompletedEvent;
import com.builddash.backend.application.event.ReturnStatusChangedEvent;
import com.builddash.backend.application.service.ApprovalEligibilityResolver;
import com.builddash.backend.application.service.NotificationService;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.ReturnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Phase 7 notification trigger surface — one AFTER_COMMIT handler per event, exactly the
 * OrderConfirmedInvoiceListener discipline. Each handler resolves the recipient off the
 * parent aggregate (ids-only payloads, OQ-2), maps the moment to its NotificationEventType,
 * and hands off to NotificationService, which owns the guard + PENDING log row + enqueue.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationTriggerListener {

    private final OrderRepository orderRepository;
    private final ReturnRepository returnRepository;
    private final NotificationService notificationService;
    private final ApprovalEligibilityResolver approvalEligibilityResolver;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderPacked(OrderPackedEvent event) {
        if (event == null) {
            return;
        }
        notifyOrder(event.orderId(), NotificationEventType.ORDER_PACKED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderDispatched(OrderDispatchedEvent event) {
        if (event == null) {
            return;
        }
        notifyOrder(event.orderId(), NotificationEventType.ORDER_DISPATCHED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderDelivered(OrderDeliveredEvent event) {
        if (event == null) {
            return;
        }
        notifyOrder(event.orderId(), NotificationEventType.ORDER_DELIVERED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderCancelled(OrderCancelledEvent event) {
        if (event == null) {
            return;
        }
        // Both origins (CUSTOMER_WINDOW, DELIVERY_WEBHOOK) collapse to one moment — an
        // order cancels once, so the (eventType, referenceId) guard holds either way.
        notifyOrder(event.orderId(), NotificationEventType.ORDER_CANCELLED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReturnStatusChanged(ReturnStatusChangedEvent event) {
        if (event == null) {
            return;
        }
        NotificationEventType eventType = NotificationEventType.fromReturnStatus(event.to());
        if (eventType == null) {
            // REQUESTED is never published and REFUND_COMPLETED is owned by onRefundCompleted.
            return;
        }
        returnRepository.findById(event.returnId())
                .ifPresentOrElse(
                        ret -> notificationService.notify(ret.userId(), eventType, ret.id()),
                        () -> log.warn("Return {} not found for {}, skipping notification", event.returnId(), eventType));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRefundCompleted(RefundCompletedEvent event) {
        if (event == null) {
            return;
        }
        returnRepository.findById(event.returnId())
                .ifPresentOrElse(
                        ret -> notificationService.notify(ret.userId(), NotificationEventType.REFUND_COMPLETED, ret.id()),
                        () -> log.warn("Return {} not found for REFUND_COMPLETED, skipping notification", event.returnId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInvoiceReady(InvoiceReadyEvent event) {
        if (event == null) {
            return;
        }
        notifyOrder(event.orderId(), NotificationEventType.INVOICE_READY);
    }

    private void notifyOrder(UUID orderId, NotificationEventType eventType) {
        orderRepository.findById(orderId)
                .ifPresentOrElse(
                        order -> notificationService.notify(order.userId(), eventType, order.id()),
                        () -> log.warn("Order {} not found for {}, skipping notification", orderId, eventType));
    }

    // 9-D: approver fan-out — recipients resolved AFTER_COMMIT against live eligibility
    // (membership, APPROVAL_ACT, site scope, placer exclusion), so a member removed while
    // the request pends is simply not notified. Per-user guard in NotificationService
    // keeps the fan-out idempotent.

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApprovalRequested(ApprovalRequestedEvent event) {
        if (event == null) {
            return;
        }
        notifyApprovers(event.companyId(), event.stageRole(), event.siteId(), event.placerUserId(),
                event.requestId(), NotificationEventType.APPROVAL_REQUESTED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApprovalEscalated(ApprovalEscalatedEvent event) {
        if (event == null) {
            return;
        }
        notifyApprovers(event.companyId(), event.stageRole(), event.siteId(), event.placerUserId(),
                event.requestId(), NotificationEventType.APPROVAL_ESCALATED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApprovalDecided(ApprovalDecidedEvent event) {
        if (event == null) {
            return;
        }
        notificationService.notify(event.placerUserId(), NotificationEventType.APPROVAL_DECIDED,
                event.requestId());
    }

    private void notifyApprovers(UUID companyId, CompanyRole stageRole, UUID siteId, UUID placerUserId,
                                 UUID requestId, NotificationEventType eventType) {
        int sent = 0;
        for (CompanyMember member : approvalEligibilityResolver.eligibleApprovers(
                companyId, stageRole, siteId, placerUserId)) {
            notificationService.notify(member.userId(), eventType, requestId);
            sent++;
        }
        if (sent == 0) {
            log.info("No eligible approver for {} request {} — notification skipped", eventType, requestId);
        }
    }
}
