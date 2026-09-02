package com.builddash.backend.application.listener;

import com.builddash.backend.application.event.InvoiceReadyEvent;
import com.builddash.backend.application.event.OrderCancelledEvent;
import com.builddash.backend.application.event.OrderDeliveredEvent;
import com.builddash.backend.application.event.OrderDispatchedEvent;
import com.builddash.backend.application.event.OrderPackedEvent;
import com.builddash.backend.application.event.RefundCompletedEvent;
import com.builddash.backend.application.event.ReturnStatusChangedEvent;
import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.enums.NotificationStatus;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.Return;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.NotificationLogRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.ReturnRepository;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.infra.persistence.entity.NotificationLogEntity;
import com.builddash.backend.infra.persistence.repository.NotificationLogJpaRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AFTER_COMMIT + dispatch proof against real Postgres: a committed transaction's events
 * become PENDING notification_logs rows (guard, phone snapshot, channel mapping all real);
 * a rolled-back transaction's events become nothing; a duplicate event stays one row.
 * Replaces the Checkpoint A ReceiptIT — assertions moved from the recorder fake to the
 * log table itself, which is the stronger claim.
 */
class NotificationTriggerListenerDispatchIT extends AbstractIntegrationTest {

    private static final AtomicInteger PHONE_SEQ = new AtomicInteger();

    @Autowired
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private com.builddash.backend.domain.port.AddressRepository addressRepository;

    @Autowired
    private ReturnRepository returnRepository;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Autowired
    private NotificationLogJpaRepository notificationLogJpaRepository;

    /**
     * Mocked so rows stay PENDING deterministically: the real RabbitNotificationDispatchQueue
     * would hand the message to the real consumer, whose async markSent races the assertion
     * (and needs a live broker). Publish-shape is proven in the E2E test, flips here.
     */
    @MockBean
    private com.builddash.backend.domain.port.NotificationDispatchQueue dispatchQueue;

    private UUID userId;
    private UUID orderId;
    private UUID returnId;

    @BeforeEach
    void seedParentAggregates() {
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        returnId = UUID.randomUUID();

        User user = new User();
        user.setPhone("+9198" + String.format("%08d", PHONE_SEQ.incrementAndGet()));
        userId = userRepository.save(user).getId();

        UUID addressId = addressRepository.save(new com.builddash.backend.domain.model.Address(
                UUID.randomUUID(), userId, "HOME", "123 Street", null, "City", "State", "12345", 12.34, 56.78, true)).id();

        orderRepository.save(new Order(orderId, userId, addressId, UUID.fromString("11111111-1111-1111-1111-111111111101"),
                LocalDate.now(), BigDecimal.TEN, OrderStatus.CONFIRMED,
                UUID.randomUUID(), Instant.now(), null, null, List.of()));

        returnRepository.save(new Return(returnId, orderId, userId, ReturnStatus.PICKED_UP,
                ReturnReason.DAMAGED, List.of(), List.of(), Instant.now(), Instant.now()));
    }

    @Test
    void committedTransaction_allSevenEventsBecomePendingRows() {
        transactionTemplate.executeWithoutResult(tx -> {
            eventPublisher.publishEvent(new OrderPackedEvent(orderId));
            eventPublisher.publishEvent(new OrderDispatchedEvent(orderId));
            eventPublisher.publishEvent(new OrderDeliveredEvent(orderId));
            eventPublisher.publishEvent(new OrderCancelledEvent(orderId, OrderCancelledEvent.OrderCancellationOrigin.CUSTOMER_WINDOW));
            eventPublisher.publishEvent(new ReturnStatusChangedEvent(returnId, ReturnStatus.PICKED_UP, ReturnStatus.QC));
            eventPublisher.publishEvent(new RefundCompletedEvent(returnId, UUID.randomUUID()));
            eventPublisher.publishEvent(new InvoiceReadyEvent(orderId));
        });

        List<NotificationLogEntity> rows = notificationLogJpaRepository.findAll();
        List<NotificationLogEntity> mine = rows.stream().filter(r -> r.getUserId().equals(userId)).toList();
        assertThat(mine).hasSize(7);
        assertThat(mine).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(row.getChannel()).isEqualTo(com.builddash.backend.domain.enums.NotificationChannel.WHATSAPP);
            assertThat(row.getSentAt()).isNull();
        });
        assertThat(mine).extracting(NotificationLogEntity::getEventType).containsExactlyInAnyOrder(
                NotificationEventType.ORDER_PACKED, NotificationEventType.ORDER_DISPATCHED,
                NotificationEventType.ORDER_DELIVERED, NotificationEventType.ORDER_CANCELLED,
                NotificationEventType.RETURN_IN_QC, NotificationEventType.REFUND_COMPLETED,
                NotificationEventType.INVOICE_READY);
    }

    @Test
    void rolledBackTransaction_noRowsAreWritten() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(tx -> {
            eventPublisher.publishEvent(new OrderPackedEvent(orderId));
            eventPublisher.publishEvent(new InvoiceReadyEvent(orderId));
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(rowsForUser()).isEmpty();
    }

    @Test
    void duplicateEventPublish_singleRowByGuard() {
        transactionTemplate.executeWithoutResult(tx -> {
            eventPublisher.publishEvent(new OrderPackedEvent(orderId));
            eventPublisher.publishEvent(new OrderPackedEvent(orderId));
        });

        assertThat(rowsForUser())
                .hasSize(1)
                .first()
                .satisfies(row -> assertThat(row.getEventType()).isEqualTo(NotificationEventType.ORDER_PACKED));
    }

    @Test
    void markSentAndMarkFailed_flipStatusAgainstRealPostgres() {
        NotificationLogEntity row = new NotificationLogEntity();
        row.setUserId(userId);
        row.setRecipientPhone("+911234567890");
        row.setChannel(com.builddash.backend.domain.enums.NotificationChannel.WHATSAPP);
        row.setEventType(NotificationEventType.ORDER_PACKED);
        row.setReferenceId(UUID.randomUUID());
        UUID logId = notificationLogJpaRepository.save(row).getId();

        notificationLogRepository.markSent(logId);
        NotificationLogEntity sent = notificationLogJpaRepository.findById(logId).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(sent.getSentAt()).isNotNull();

        notificationLogRepository.markFailed(logId);
        NotificationLogEntity failed = notificationLogJpaRepository.findById(logId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    private List<NotificationLogEntity> rowsForUser() {
        return notificationLogJpaRepository.findAll().stream().filter(r -> r.getUserId().equals(userId)).toList();
    }
}
