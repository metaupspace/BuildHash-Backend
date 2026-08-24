package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.PaymentStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.Payment;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentWebhookServiceImplTest {

    private OrderRepository orderRepository;
    private PaymentRepository paymentRepository;
    private DeliverySlotService deliverySlotService;
    private PaymentWebhookServiceImpl webhookService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        deliverySlotService = mock(DeliverySlotService.class);
        webhookService = new PaymentWebhookServiceImpl(orderRepository, paymentRepository, deliverySlotService);
    }

    @Test
    void handleWebhook_success_confirmsOrderAndUpdatesPayment() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID lockId = UUID.randomUUID();
        
        Order order = new Order(orderId, userId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), BigDecimal.TEN, OrderStatus.PAYMENT_PENDING, lockId, java.time.Instant.now(), null, null, List.of());
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        
        Payment payment = new Payment(UUID.randomUUID(), orderId, "tx1", BigDecimal.TEN, PaymentStatus.PENDING, "url");
        when(paymentRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(payment));

        webhookService.handleWebhook(orderId, "SUCCESS", "sig");

        verify(orderRepository).save(any(Order.class));
        verify(paymentRepository).save(any(Payment.class));
        verify(deliverySlotService).releaseLock(lockId, userId);
    }

    @Test
    void handleWebhook_failed_updatesPaymentOnly() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID lockId = UUID.randomUUID();
        
        Order order = new Order(orderId, userId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), BigDecimal.TEN, OrderStatus.PAYMENT_PENDING, lockId, java.time.Instant.now(), null, null, List.of());
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        
        Payment payment = new Payment(UUID.randomUUID(), orderId, "tx1", BigDecimal.TEN, PaymentStatus.PENDING, "url");
        when(paymentRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(payment));

        webhookService.handleWebhook(orderId, "FAILED", "sig");

        verify(orderRepository, never()).save(any(Order.class));
        verify(paymentRepository).save(any(Payment.class));
        verify(deliverySlotService, never()).releaseLock(any(), any());
    }
}
