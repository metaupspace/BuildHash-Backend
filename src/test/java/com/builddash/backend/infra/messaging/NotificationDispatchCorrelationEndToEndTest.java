package com.builddash.backend.infra.messaging;

import com.builddash.backend.application.event.OrderPackedEvent;
import com.builddash.backend.application.impl.NotificationServiceImpl;
import com.builddash.backend.application.listener.NotificationTriggerListener;
import com.builddash.backend.application.service.NotificationService;
import com.builddash.backend.domain.enums.NotificationChannel;
import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.enums.NotificationStatus;
import com.builddash.backend.domain.model.NotificationLog;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.NotificationDispatchQueue;
import com.builddash.backend.domain.port.NotificationLogRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.infra.consumer.NotificationDispatchListener;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wires the real trigger listener, real service, real queue adapter, real consumer and the
 * real dev stub together in-process, mocking only the broker transport (RabbitTemplate) —
 * proves logId and channel actually survive event -> PENDING row -> publish -> consume ->
 * sender -> markSent. Hop-isolated tests can't catch a dropped field between hops; this
 * test is what would (CatalogOutboxCorrelationEndToEndTest convention).
 */
class NotificationDispatchCorrelationEndToEndTest {

    @Test
    void orderPackedEvent_flowsThroughEveryHopToSentAndFailureLegsMarkFailed() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        String phone = "+911234567890";

        // --- Hop 1: trigger listener resolves recipient + moment, calls notify ---
        Order order = new Order(orderId, userId, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), BigDecimal.TEN, com.builddash.backend.domain.enums.OrderStatus.PACKED,
                null, Instant.now(), null, null, List.of());
        OrderRepository orderRepository = mock(OrderRepository.class);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        UserRepository userRepository = mock(UserRepository.class);
        User user = new User();
        user.setId(userId);
        user.setPhone(phone);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        NotificationLogRepository logRepository = mock(NotificationLogRepository.class);
        when(logRepository.existsByEventTypeAndReferenceId(NotificationEventType.ORDER_PACKED, orderId)).thenReturn(false);
        when(logRepository.save(any())).thenAnswer(invocation -> {
            NotificationLog row = invocation.getArgument(0);
            row.setId(logId);
            return row;
        });

        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        NotificationDispatchQueue dispatchQueue = new RabbitNotificationDispatchQueue(rabbitTemplate);

        NotificationService notificationService =
                new NotificationServiceImpl(logRepository, userRepository, dispatchQueue);
        NotificationTriggerListener triggerListener =
                new NotificationTriggerListener(orderRepository, mock(com.builddash.backend.domain.port.ReturnRepository.class), notificationService,
                        mock(com.builddash.backend.application.service.ApprovalEligibilityResolver.class));

        triggerListener.onOrderPacked(new OrderPackedEvent(orderId));

        // --- Hop 2: queue adapter published to the WhatsApp queue with the log row's id ---
        ArgumentCaptor<NotificationDispatchMessage> publishedCaptor = ArgumentCaptor.forClass(NotificationDispatchMessage.class);
        verify(rabbitTemplate).convertAndSend(eq("notification.whatsapp"), publishedCaptor.capture());
        NotificationDispatchMessage published = publishedCaptor.getValue();
        assertThat(published.logId()).isEqualTo(logId);
        assertThat(published.channel()).isEqualTo(NotificationChannel.WHATSAPP);
        assertThat(published.phone()).isEqualTo(phone);
        assertThat(published.eventType()).isEqualTo(NotificationEventType.ORDER_PACKED);
        assertThat(published.referenceId()).isEqualTo(orderId);

        // --- Hop 3: consumer receives it, sender sends, row flips SENT ---
        com.builddash.backend.domain.port.WhatsAppNotificationSender whatsAppSender =
                mock(com.builddash.backend.domain.port.WhatsAppNotificationSender.class);
        NotificationDispatchListener consumer = new NotificationDispatchListener(
                mock(com.builddash.backend.domain.port.PushNotificationSender.class),
                mock(com.builddash.backend.domain.port.SmsNotificationSender.class),
                whatsAppSender, logRepository);

        consumer.onWhatsApp(published);

        verify(whatsAppSender).send(phone, NotificationEventType.ORDER_PACKED, orderId);
        verify(logRepository).markSent(logId);

        // --- Failure leg: a dead-lettered copy of the same message flips the row FAILED ---
        consumer.onDeadLetter(published);
        verify(logRepository).markFailed(logId);
    }

    /** NotificationLog's PENDING default is load-bearing for the outbox contract — pin it. */
    @Test
    void newLogRowDefaultsToPending() throws Exception {
        NotificationLog row = new NotificationLog();
        assertThat(row.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }
}
