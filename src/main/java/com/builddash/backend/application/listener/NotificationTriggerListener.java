package com.builddash.backend.application.listener;

import com.builddash.backend.application.event.InvoiceReadyEvent;
import com.builddash.backend.application.event.OrderCancelledEvent;
import com.builddash.backend.application.event.OrderDeliveredEvent;
import com.builddash.backend.application.event.OrderDispatchedEvent;
import com.builddash.backend.application.event.OrderPackedEvent;
import com.builddash.backend.application.event.RefundCompletedEvent;
import com.builddash.backend.application.event.ReturnStatusChangedEvent;
import com.builddash.backend.domain.port.NotificationReceiptRecorder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Phase 7 notification trigger surface — one AFTER_COMMIT handler per event, exactly the
 * OrderConfirmedInvoiceListener discipline: fires only after the publishing transaction commits,
 * runs in its own transaction, and guards null payloads. Checkpoint A forwards each event to the
 * NotificationReceiptRecorder seam; Checkpoint B replaces the forwarding with notification
 * dispatch (idempotency guard + log row + queue publish).
 */
@Component
public class NotificationTriggerListener {

    private final NotificationReceiptRecorder recorder;

    public NotificationTriggerListener(NotificationReceiptRecorder recorder) {
        this.recorder = recorder;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderPacked(OrderPackedEvent event) {
        if (event == null) {
            return;
        }
        recorder.record(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderDispatched(OrderDispatchedEvent event) {
        if (event == null) {
            return;
        }
        recorder.record(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderDelivered(OrderDeliveredEvent event) {
        if (event == null) {
            return;
        }
        recorder.record(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderCancelled(OrderCancelledEvent event) {
        if (event == null) {
            return;
        }
        recorder.record(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReturnStatusChanged(ReturnStatusChangedEvent event) {
        if (event == null) {
            return;
        }
        recorder.record(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRefundCompleted(RefundCompletedEvent event) {
        if (event == null) {
            return;
        }
        recorder.record(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInvoiceReady(InvoiceReadyEvent event) {
        if (event == null) {
            return;
        }
        recorder.record(event);
    }
}
