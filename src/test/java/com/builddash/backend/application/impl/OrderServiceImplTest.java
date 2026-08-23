package com.builddash.backend.application.impl;

import com.builddash.backend.api.dto.response.OrderResponse;
import com.builddash.backend.application.service.CheckoutIntentService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.exception.PaymentGatewayException;
import com.builddash.backend.domain.model.CheckoutIntent;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.model.PaymentReference;
import com.builddash.backend.domain.model.PricedCart;
import com.builddash.backend.domain.port.IdempotencyKeyRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.PaymentRepository;
import com.builddash.backend.domain.port.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private CheckoutIntentService checkoutIntentService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private OrderServiceImpl orderService;

    private final UUID userId = UUID.randomUUID();
    private final UUID addressId = UUID.randomUUID();
    private final UUID slotId = UUID.randomUUID();
    private final LocalDate slotDate = LocalDate.now();
    private final BigDecimal expectedTotal = new BigDecimal("100.00");
    private final String idempotencyKey = "test-key";

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Order> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void create_whenIdempotencyKeyExists_returnsExistingOrderAndInitiatesPayment() {
        UUID existingOrderId = UUID.randomUUID();
        Order existingOrder = new Order(existingOrderId, userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), List.of());

        when(idempotencyKeyRepository.findOrderId(idempotencyKey)).thenReturn(Optional.of(existingOrderId));
        when(orderRepository.findById(existingOrderId)).thenReturn(Optional.of(existingOrder));
        when(paymentGateway.initiate(existingOrderId, expectedTotal)).thenReturn(new PaymentReference("tx-1", "url-1"));

        OrderResponse response = orderService.create(userId, addressId, slotId, slotDate, expectedTotal, idempotencyKey);

        assertThat(response.id()).isEqualTo(existingOrderId);
        assertThat(response.paymentUrl()).isEqualTo("url-1");
        verify(checkoutIntentService, never()).createIntent(any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void create_whenNewIdempotencyKey_createsOrderAndInitiatesPayment() {
        when(idempotencyKeyRepository.findOrderId(idempotencyKey)).thenReturn(Optional.empty());
        
        CheckoutIntent intent = mock(CheckoutIntent.class);
        when(intent.lockedTotal()).thenReturn(expectedTotal);
        PricedCart cart = mock(PricedCart.class);
        when(cart.items()).thenReturn(List.of());
        when(intent.pricedCart()).thenReturn(cart);
        
        when(checkoutIntentService.createIntent(userId, addressId, slotId, slotDate, expectedTotal)).thenReturn(intent);
        
        Order savedOrder = new Order(UUID.randomUUID(), userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        
        when(paymentGateway.initiate(savedOrder.id(), expectedTotal)).thenReturn(new PaymentReference("tx-1", "url-1"));

        OrderResponse response = orderService.create(userId, addressId, slotId, slotDate, expectedTotal, idempotencyKey);

        assertThat(response.id()).isEqualTo(savedOrder.id());
        assertThat(response.paymentUrl()).isEqualTo("url-1");
        verify(idempotencyKeyRepository).save(idempotencyKey, savedOrder.id());
    }

    @Test
    void create_whenPaymentGatewayFails_throwsPaymentGatewayException() {
        when(idempotencyKeyRepository.findOrderId(idempotencyKey)).thenReturn(Optional.empty());
        
        CheckoutIntent intent = mock(CheckoutIntent.class);
        when(intent.lockedTotal()).thenReturn(expectedTotal);
        PricedCart cart = mock(PricedCart.class);
        when(cart.items()).thenReturn(List.of());
        when(intent.pricedCart()).thenReturn(cart);
        
        when(checkoutIntentService.createIntent(userId, addressId, slotId, slotDate, expectedTotal)).thenReturn(intent);
        
        Order savedOrder = new Order(UUID.randomUUID(), userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        
        when(paymentGateway.initiate(savedOrder.id(), expectedTotal)).thenThrow(new RuntimeException("Gateway down"));

        assertThatThrownBy(() -> orderService.create(userId, addressId, slotId, slotDate, expectedTotal, idempotencyKey))
                .isInstanceOf(PaymentGatewayException.class)
                .hasFieldOrPropertyWithValue("orderId", savedOrder.id());
    }
}
