package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.PaymentStatus;
import com.builddash.backend.domain.exception.UnauthorizedException;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.Payment;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.PaymentRepository;
import com.builddash.backend.domain.port.PaymentWebhookConfig;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentWebhookServiceImplTest {

    private static final String SECRET = "test-webhook-secret-0123456789abcdef";

    private OrderRepository orderRepository;
    private PaymentRepository paymentRepository;
    private DeliverySlotService deliverySlotService;
    private com.builddash.backend.domain.port.InvoiceRepository invoiceRepository;
    private com.builddash.backend.domain.port.PaymentReconciliationRepository paymentReconciliationRepository;
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    private PaymentWebhookServiceImpl webhookService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        deliverySlotService = mock(DeliverySlotService.class);
        invoiceRepository = mock(com.builddash.backend.domain.port.InvoiceRepository.class);
        paymentReconciliationRepository = mock(com.builddash.backend.domain.port.PaymentReconciliationRepository.class);
        eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        webhookService = new PaymentWebhookServiceImpl(
                orderRepository, paymentRepository, deliverySlotService, () -> SECRET, eventPublisher, invoiceRepository, paymentReconciliationRepository, org.mockito.Mockito.mock(com.builddash.backend.application.service.ApplicationMetrics.class));
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
    void handleWebhook_validSignature_success_confirmsOrderAndUpdatesPayment() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID lockId = UUID.randomUUID();

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(pendingOrder(orderId, userId, lockId)));

        Payment payment = new Payment(UUID.randomUUID(), orderId, "tx1", BigDecimal.TEN, PaymentStatus.PENDING, "url");
        when(paymentRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(payment));

        webhookService.handleWebhook(orderId, "SUCCESS", sign(orderId, "SUCCESS"));

        verify(orderRepository).save(any(Order.class));
        verify(paymentRepository).save(any(Payment.class));
        // SUCCESS consumes the slot (capacity stays held for the delivery) — never released
        verify(deliverySlotService).consumeLock(lockId, userId);
        verify(deliverySlotService, never()).releaseLock(any(), any());
    }

    @Test
    void handleWebhook_validSignature_failed_updatesPaymentOnly() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID lockId = UUID.randomUUID();

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(pendingOrder(orderId, userId, lockId)));

        Payment payment = new Payment(UUID.randomUUID(), orderId, "tx1", BigDecimal.TEN, PaymentStatus.PENDING, "url");
        when(paymentRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(payment));

        webhookService.handleWebhook(orderId, "FAILED", sign(orderId, "FAILED"));

        verify(orderRepository, never()).save(any(Order.class));
        verify(paymentRepository).save(any(Payment.class));
        verify(deliverySlotService, never()).releaseLock(any(), any());
    }

    @Test
    void handleWebhook_invalidSignature_throwsAndChangesNothing() {
        UUID orderId = UUID.randomUUID();

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(pendingOrder(orderId, UUID.randomUUID(), UUID.randomUUID())));

        assertThatThrownBy(() -> webhookService.handleWebhook(orderId, "SUCCESS", "deadbeef"))
                .isInstanceOf(UnauthorizedException.class);

        verify(orderRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
        verify(deliverySlotService, never()).releaseLock(any(), any());
    }

    @Test
    void handleWebhook_signatureWithWrongSecret_rejected() {
        UUID orderId = UUID.randomUUID();

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(pendingOrder(orderId, UUID.randomUUID(), UUID.randomUUID())));

        // signature computed with a different secret
        String forged;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("attacker-secret-0123456789abcdef!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            forged = HexFormat.of().formatHex(mac.doFinal((orderId + ":SUCCESS").getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        assertThatThrownBy(() -> webhookService.handleWebhook(orderId, "SUCCESS", forged))
                .isInstanceOf(UnauthorizedException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void handleWebhook_nullOrBlankSignature_rejected() {
        UUID orderId = UUID.randomUUID();

        when(orderRepository.findByIdForUpdate(orderId))
                .thenReturn(Optional.of(pendingOrder(orderId, UUID.randomUUID(), UUID.randomUUID())));

        assertThatThrownBy(() -> webhookService.handleWebhook(orderId, "SUCCESS", null))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> webhookService.handleWebhook(orderId, "SUCCESS", "  "))
                .isInstanceOf(UnauthorizedException.class);

        verify(orderRepository, never()).save(any());
    }
}
