package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.application.service.PaymentReconciliationService;
import com.builddash.backend.application.service.StaleOrderSweepService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaleOrderSweepServiceImpl implements StaleOrderSweepService {

    private final OrderRepository orderRepository;
    private final DeliverySlotService deliverySlotService;
    private final com.builddash.backend.domain.port.DeliverySlotLockRepository deliverySlotLockRepository;
    private final com.builddash.backend.domain.port.DeliverySlotCounterRepository deliverySlotCounterRepository;
    private final PaymentReconciliationService paymentReconciliationService;
    /**
     * Invariant seam only — never called by this service. A stale PAYMENT_PENDING cancel is not a
     * customer-facing transition (the order was never confirmed or paid), so it must publish no
     * events; StaleOrderSweepServiceImplTest.verifyNoInteractions keeps that permanent.
     */
    private final ApplicationEventPublisher eventPublisher;
    private @org.springframework.context.annotation.Lazy @org.springframework.beans.factory.annotation.Autowired StaleOrderSweepServiceImpl self; // Self-injection for REQUIRED_NEW transaction boundary

    @Override
    public void sweepStaleOrders() {
        Instant cutoff = Instant.now();
        List<UUID> staleOrderIds = orderRepository.findStalePaymentPendingOrderIds(cutoff);

        if (staleOrderIds.isEmpty()) {
            return;
        }

        log.info("Found {} stale PAYMENT_PENDING orders", staleOrderIds.size());

        for (UUID orderId : staleOrderIds) {
            try {
                // H10.2: Reconcile in-flight payment with gateway before cancelling
                PaymentReconciliationService.ReconciliationOutcome outcome =
                        paymentReconciliationService.reconcileStalePendingPayment(orderId);
                if (outcome == PaymentReconciliationService.ReconciliationOutcome.CONFIRMED) {
                    log.info("Stale order {} was reconciled to CONFIRMED via gateway, skipping cancellation", orderId);
                    continue;
                } else if (outcome == PaymentReconciliationService.ReconciliationOutcome.AMBIGUOUS_HOLD) {
                    log.info("Stale order {} has ambiguous or pending gateway status, holding without cancellation", orderId);
                    continue;
                }
                self.sweepOrder(orderId);
            } catch (Exception e) {
                log.error("Failed to sweep order {}", orderId, e);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sweepOrder(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElse(null);

        if (order == null || order.status() != OrderStatus.PAYMENT_PENDING) {
            return;
        }

        log.info("Cancelling stale order {}", orderId);
        Order cancelled = order.cancel();
        orderRepository.save(cancelled);

        // H2.3: the release must NOT run inside this transaction. releaseLock joins the
        // caller's transaction, so an exception inside it marks this tx rollback-only —
        // the catch below would swallow the exception but the commit would still fail,
        // un-cancelling the order (the rollback trap). Route it through the proxy so it
        // gets a real REQUIRES_NEW boundary: a failed release rolls back alone, and the
        // CANCELLED state survives durably.
        try {
            self.releaseLockForOrder(cancelled.deliverySlotLockId(), cancelled.userId());
        } catch (Exception e) {
            log.warn("Could not release slot lock {} for order {} (order stays CANCELLED)",
                    cancelled.deliverySlotLockId(), orderId, e);
        }
    }

    /**
     * H2.3: best-effort slot release on its own transaction — a failure here must never
     * poison the sweep transaction that cancelled the order.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseLockForOrder(UUID lockId, UUID userId) {
        deliverySlotService.releaseLock(lockId, userId);
    }

    @Override
    public void sweepExpiredLocks() {
        List<com.builddash.backend.domain.model.DeliverySlotLock> expired =
                deliverySlotLockRepository.findExpiredActiveLocks(Instant.now());
        if (expired.isEmpty()) {
            return;
        }
        log.info("Sweeping {} expired delivery-slot locks", expired.size());

        for (com.builddash.backend.domain.model.DeliverySlotLock lock : expired) {
            try {
                self.sweepExpiredLock(lock.id(), lock.slotId(), lock.slotDate());
            } catch (Exception e) {
                log.error("Failed to sweep expired lock {}", lock.id(), e);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sweepExpiredLock(UUID lockId, UUID slotId, java.time.LocalDate slotDate) {
        // H2.4: counter row locked before the lock row (same global row order as
        // releaseLock), and the decrement rides on winning the ACTIVE -> EXPIRED CAS —
        // the lock may have been released or consumed between the expired-lock read
        // above and this transaction, and that winner already returned the capacity.
        // A missing counter row still expires the lock (as before) — there is no
        // capacity row to reconcile against.
        var counter = deliverySlotCounterRepository.findBySlotIdAndSlotDateForUpdate(slotId, slotDate);
        if (deliverySlotLockRepository.tryTransitionStatus(lockId,
                com.builddash.backend.domain.enums.DeliverySlotLockStatus.ACTIVE,
                com.builddash.backend.domain.enums.DeliverySlotLockStatus.EXPIRED) == 1) {
            counter.ifPresent(c -> deliverySlotCounterRepository.save(c.decrement()));
        }
    }
}
