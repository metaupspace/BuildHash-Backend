package com.builddash.backend.application.listener;

import com.builddash.backend.application.event.InvoiceReadyEvent;
import com.builddash.backend.application.event.OrderCancelledEvent;
import com.builddash.backend.application.event.OrderDeliveredEvent;
import com.builddash.backend.application.event.OrderDispatchedEvent;
import com.builddash.backend.application.event.OrderPackedEvent;
import com.builddash.backend.application.event.RefundCompletedEvent;
import com.builddash.backend.application.event.ReturnStatusChangedEvent;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.port.NotificationReceiptRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationTriggerListenerTest {

    @Mock
    private NotificationReceiptRecorder recorder;

    private NotificationTriggerListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationTriggerListener(recorder);
    }

    @Test
    void everyHandler_passesExactEventInstanceToRecorder() {
        UUID orderId = UUID.randomUUID();
        UUID returnId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        List<Object> events = List.of(
                new OrderPackedEvent(orderId),
                new OrderDispatchedEvent(orderId),
                new OrderDeliveredEvent(orderId),
                new OrderCancelledEvent(orderId, OrderCancelledEvent.OrderCancellationOrigin.DELIVERY_WEBHOOK),
                new ReturnStatusChangedEvent(returnId, ReturnStatus.QC, ReturnStatus.REFUND_INITIATED),
                new RefundCompletedEvent(returnId, refundId),
                new InvoiceReadyEvent(orderId));

        listener.onOrderPacked((OrderPackedEvent) events.get(0));
        listener.onOrderDispatched((OrderDispatchedEvent) events.get(1));
        listener.onOrderDelivered((OrderDeliveredEvent) events.get(2));
        listener.onOrderCancelled((OrderCancelledEvent) events.get(3));
        listener.onReturnStatusChanged((ReturnStatusChangedEvent) events.get(4));
        listener.onRefundCompleted((RefundCompletedEvent) events.get(5));
        listener.onInvoiceReady((InvoiceReadyEvent) events.get(6));

        for (Object event : events) {
            verify(recorder).record(event);
        }
    }

    @Test
    void everyHandler_nullEvent_recordsNothing() {
        listener.onOrderPacked(null);
        listener.onOrderDispatched(null);
        listener.onOrderDelivered(null);
        listener.onOrderCancelled(null);
        listener.onReturnStatusChanged(null);
        listener.onRefundCompleted(null);
        listener.onInvoiceReady(null);

        verifyNoInteractions(recorder);
    }
}
