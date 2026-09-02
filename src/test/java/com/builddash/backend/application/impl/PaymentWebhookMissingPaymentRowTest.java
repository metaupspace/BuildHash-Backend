package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.Payment;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H1.2: CONFIRMED must correspond to a durable payment record. A SUCCESS webhook must
 * never confirm an order when no Payment row exists for it at all.
 */
class PaymentWebhookMissingPaymentRowTest {

    private static final String SECRET = "test-webhook-secret-0123456789abcdef";

    private OrderRepository orderRepository;
    private PaymentRepository paymentRepository;
    private DeliverySlotService deliverySlotService;
    private com.builddash.backend.domain.port.InvoiceRepository invoiceRepository;
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    private PaymentWebhookServiceImpl webhookService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        deliverySlotService = mock(DeliverySlotService.class);
        invoiceRepository = mock(com.builddash.backend.domain.port.InvoiceRepository.class);
        eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        webhookService = new PaymentWebhookServiceImpl(
                orderRepository, paymentRepository, deliverySlotService, () -> SECRET, eventPublisher, invoiceRepository);
    }

    private static String sign(UUID orderId, String status) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = orderId + ":" + status;
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Order pendingOrder(UUID orderId, UUID userId, UUID lockId) {
        return new Order(orderId, userId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                BigDecimal.TEN, OrderStatus.PAYMENT_PENDING, lockId, Instant.now(), null, null, List.of());
    }

    @Test
    void handleWebhook_successWithNoPaymentRow_doesNotConfirmOrder() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID lockId = UUID.randomUUID();

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(pendingOrder(orderId, userId, lockId)));
        when(paymentRepository.findLatestByOrderId(orderId)).thenReturn(Optional.empty());

        webhookService.handleWebhook(orderId, "SUCCESS", sign(orderId, "SUCCESS"));

        verify(orderRepository, never()).save(any(Order.class));
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(deliverySlotService, never()).consumeLock(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void handleWebhook_successWithPaymentRowPresent_stillConfirms() {
        // Regression guard: the H1.2 fix must not affect the ordinary path where a
        // Payment row does exist.
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID lockId = UUID.randomUUID();

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(pendingOrder(orderId, userId, lockId)));
        Payment payment = new Payment(UUID.randomUUID(), orderId, "tx1", BigDecimal.TEN,
                com.builddash.backend.domain.enums.PaymentStatus.PENDING, "url");
        when(paymentRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(payment));

        webhookService.handleWebhook(orderId, "SUCCESS", sign(orderId, "SUCCESS"));

        verify(orderRepository).save(any(Order.class));
        verify(paymentRepository).save(any(Payment.class));
        verify(deliverySlotService).consumeLock(lockId, userId);
    }
}
