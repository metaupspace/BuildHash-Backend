package com.builddash.backend.domain.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the locked OQ-11 mapping (12 WhatsApp moments + CART_ABANDONED as the SMS side,
 * producer-backed since Checkpoint C) and the ReturnStatus -> enum granularity the
 * (eventType, referenceId) guard depends on.
 */
class NotificationEventTypeTest {

    @Test
    void everyValue_resolvesItsLockedChannel() {
        assertThat(NotificationEventType.values())
                .allSatisfy(type -> assertThat(type.channel()).isNotNull());

        List<NotificationEventType> whatsApp = Arrays.stream(NotificationEventType.values())
                .filter(type -> type.channel() == NotificationChannel.WHATSAPP)
                .toList();
        assertThat(whatsApp).hasSize(12);
        assertThat(NotificationEventType.CART_ABANDONED.channel()).isEqualTo(NotificationChannel.SMS);
    }

    @Test
    void enumCoversExactlyTheThirteenProducerBackedMoments() {
        List<String> names = Arrays.stream(NotificationEventType.values()).map(Enum::name).toList();

        assertThat(names).containsExactlyInAnyOrder(
                "ORDER_PACKED", "ORDER_DISPATCHED", "ORDER_DELIVERED", "ORDER_CANCELLED",
                "RETURN_APPROVED", "RETURN_PICKUP_SCHEDULED", "RETURN_PICKED_UP", "RETURN_IN_QC", "RETURN_REJECTED",
                "REFUND_INITIATED", "REFUND_COMPLETED", "INVOICE_READY", "CART_ABANDONED");
    }

    @Test
    void everyNotifiableReturnStatus_mapsToExactlyOneEventType() {
        List<ReturnStatus> notifiable = List.of(
                ReturnStatus.APPROVED, ReturnStatus.PICKUP_SCHEDULED, ReturnStatus.PICKED_UP,
                ReturnStatus.QC, ReturnStatus.REJECTED, ReturnStatus.REFUND_INITIATED);

        assertThat(notifiable)
                .allSatisfy(status -> assertThat(NotificationEventType.fromReturnStatus(status)).isNotNull());
        assertThat(NotificationEventType.fromReturnStatus(ReturnStatus.APPROVED)).isEqualTo(NotificationEventType.RETURN_APPROVED);
        assertThat(NotificationEventType.fromReturnStatus(ReturnStatus.PICKUP_SCHEDULED)).isEqualTo(NotificationEventType.RETURN_PICKUP_SCHEDULED);
        assertThat(NotificationEventType.fromReturnStatus(ReturnStatus.PICKED_UP)).isEqualTo(NotificationEventType.RETURN_PICKED_UP);
        assertThat(NotificationEventType.fromReturnStatus(ReturnStatus.QC)).isEqualTo(NotificationEventType.RETURN_IN_QC);
        assertThat(NotificationEventType.fromReturnStatus(ReturnStatus.REJECTED)).isEqualTo(NotificationEventType.RETURN_REJECTED);
        assertThat(NotificationEventType.fromReturnStatus(ReturnStatus.REFUND_INITIATED)).isEqualTo(NotificationEventType.REFUND_INITIATED);
    }

    @Test
    void unmappedReturnStatuses_mapToNull_notDuplicateNotifications() {
        // REQUESTED: never published (customer's own action). REFUND_COMPLETED: owned by the
        // RefundCompletedEvent handler — mapping it here would double-notify from completeRefund.
        assertThat(NotificationEventType.fromReturnStatus(ReturnStatus.REQUESTED)).isNull();
        assertThat(NotificationEventType.fromReturnStatus(ReturnStatus.REFUND_COMPLETED)).isNull();
    }
}
