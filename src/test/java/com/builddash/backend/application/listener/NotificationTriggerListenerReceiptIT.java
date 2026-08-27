package com.builddash.backend.application.listener;

import com.builddash.backend.application.event.InvoiceReadyEvent;
import com.builddash.backend.application.event.OrderCancelledEvent;
import com.builddash.backend.application.event.OrderDeliveredEvent;
import com.builddash.backend.application.event.OrderDispatchedEvent;
import com.builddash.backend.application.event.OrderPackedEvent;
import com.builddash.backend.application.event.RefundCompletedEvent;
import com.builddash.backend.application.event.ReturnStatusChangedEvent;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.builddash.backend.support.RecordingNotificationReceiptRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AFTER_COMMIT proof for the notification trigger surface: events published inside a COMMITTED
 * transaction reach the listener (and thus the recorder); the same events published inside a
 * transaction that ROLLS BACK reach nothing. The recording fake is @Primary, so the Spring
 * context wires it in place of LoggingNotificationReceiptRecorder.
 */
class NotificationTriggerListenerReceiptIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private RecordingNotificationReceiptRecorder recorder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void clearRecorder() {
        recorder.clear();
    }

    private List<Object> allSevenEvents(UUID orderId, UUID returnId, UUID refundId) {
        return List.of(
                new OrderPackedEvent(orderId),
                new OrderDispatchedEvent(orderId),
                new OrderDeliveredEvent(orderId),
                new OrderCancelledEvent(orderId, OrderCancelledEvent.OrderCancellationOrigin.CUSTOMER_WINDOW),
                new ReturnStatusChangedEvent(returnId, ReturnStatus.QC, ReturnStatus.REFUND_INITIATED),
                new RefundCompletedEvent(returnId, refundId),
                new InvoiceReadyEvent(orderId));
    }

    @Test
    void committedTransaction_allSevenEventsReachTheListener() {
        List<Object> events = allSevenEvents(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        transactionTemplate.executeWithoutResult(tx -> events.forEach(eventPublisher::publishEvent));

        assertThat(recorder.captured()).containsExactlyInAnyOrderElementsOf(events);
    }

    @Test
    void rolledBackTransaction_nothingReachesTheListener() {
        List<Object> events = allSevenEvents(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(tx -> {
            events.forEach(eventPublisher::publishEvent);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(recorder.captured()).isEmpty();
    }
}
