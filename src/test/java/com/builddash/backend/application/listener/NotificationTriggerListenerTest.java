package com.builddash.backend.application.listener;

import com.builddash.backend.application.event.InvoiceReadyEvent;
import com.builddash.backend.application.event.OrderCancelledEvent;
import com.builddash.backend.application.event.OrderDeliveredEvent;
import com.builddash.backend.application.event.OrderDispatchedEvent;
import com.builddash.backend.application.event.OrderPackedEvent;
import com.builddash.backend.application.event.RefundCompletedEvent;
import com.builddash.backend.application.event.ReturnStatusChangedEvent;
import com.builddash.backend.application.service.NotificationService;
import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.Return;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.ReturnRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Checkpoint B rewrite: the recorder seam is gone; handlers now resolve the recipient off
 * the parent aggregate and call NotificationService.notify with (userId, eventType, referenceId).
 */
@ExtendWith(MockitoExtension.class)
class NotificationTriggerListenerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ReturnRepository returnRepository;

    @Mock
    private NotificationService notificationService;

    private NotificationTriggerListener listener;

    private final UUID orderId = UUID.randomUUID();
    private final UUID orderUserId = UUID.randomUUID();
    private final UUID returnId = UUID.randomUUID();
    private final UUID returnUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new NotificationTriggerListener(orderRepository, returnRepository, notificationService);
    }

    private Order order() {
        return new Order(orderId, orderUserId, UUID.randomUUID(), UUID.randomUUID(),
                java.time.LocalDate.now(), java.math.BigDecimal.TEN, com.builddash.backend.domain.enums.OrderStatus.PACKED,
                null, Instant.now(), null, null, List.of());
    }

    private Return returnObj() {
        return new Return(returnId, orderId, returnUserId, ReturnStatus.QC,
                com.builddash.backend.domain.enums.ReturnReason.DAMAGED, List.of(), List.of(), Instant.now(), Instant.now());
    }

    @Test
    void everyHandler_resolvesRecipientAndCallsNotifyWithExactIds() {
        org.mockito.Mockito.when(orderRepository.findById(orderId)).thenReturn(Optional.of(order()));
        org.mockito.Mockito.when(returnRepository.findById(returnId)).thenReturn(Optional.of(returnObj()));

        listener.onOrderPacked(new OrderPackedEvent(orderId));
        listener.onOrderDispatched(new OrderDispatchedEvent(orderId));
        listener.onOrderDelivered(new OrderDeliveredEvent(orderId));
        listener.onOrderCancelled(new OrderCancelledEvent(orderId, OrderCancelledEvent.OrderCancellationOrigin.CUSTOMER_WINDOW));
        listener.onReturnStatusChanged(new ReturnStatusChangedEvent(returnId, ReturnStatus.PICKED_UP, ReturnStatus.QC));
        listener.onRefundCompleted(new RefundCompletedEvent(returnId, UUID.randomUUID()));
        listener.onInvoiceReady(new InvoiceReadyEvent(orderId));

        org.mockito.Mockito.verify(notificationService).notify(orderUserId, NotificationEventType.ORDER_PACKED, orderId);
        org.mockito.Mockito.verify(notificationService).notify(orderUserId, NotificationEventType.ORDER_DISPATCHED, orderId);
        org.mockito.Mockito.verify(notificationService).notify(orderUserId, NotificationEventType.ORDER_DELIVERED, orderId);
        org.mockito.Mockito.verify(notificationService).notify(orderUserId, NotificationEventType.ORDER_CANCELLED, orderId);
        org.mockito.Mockito.verify(notificationService).notify(returnUserId, NotificationEventType.RETURN_IN_QC, returnId);
        org.mockito.Mockito.verify(notificationService).notify(returnUserId, NotificationEventType.REFUND_COMPLETED, returnId);
        org.mockito.Mockito.verify(notificationService).notify(orderUserId, NotificationEventType.INVOICE_READY, orderId);
        org.mockito.Mockito.verifyNoMoreInteractions(notificationService);
    }

    @Test
    void returnTransitionToRefundCompleted_skipsThatMomentIsOwnedByRefundCompletedEvent() {
        listener.onReturnStatusChanged(new ReturnStatusChangedEvent(returnId, ReturnStatus.QC, ReturnStatus.REFUND_COMPLETED));

        verifyNoInteractions(notificationService, returnRepository);
    }

    @Test
    void nullEvent_everyHandlerRecordsNothing() {
        listener.onOrderPacked(null);
        listener.onOrderDispatched(null);
        listener.onOrderDelivered(null);
        listener.onOrderCancelled(null);
        listener.onReturnStatusChanged(null);
        listener.onRefundCompleted(null);
        listener.onInvoiceReady(null);

        verifyNoInteractions(notificationService, orderRepository, returnRepository);
    }

    @Test
    void missingParentAggregate_skipsWithoutFailing() {
        org.mockito.Mockito.when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        listener.onOrderPacked(new OrderPackedEvent(orderId));

        verifyNoInteractions(notificationService);
    }
}
