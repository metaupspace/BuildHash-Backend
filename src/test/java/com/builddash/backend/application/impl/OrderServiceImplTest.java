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
import com.builddash.backend.api.dto.response.OrderLineItemResponse;
import com.builddash.backend.api.dto.response.ReorderResponse;
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
    @Mock
    private com.builddash.backend.domain.port.CartPricingCalculator cartPricingCalculator;
    @Mock
    private com.builddash.backend.api.mapper.CartDtoMapper cartDtoMapper;

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
        Order existingOrder = new Order(existingOrderId, userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());

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
        
        Order savedOrder = new Order(UUID.randomUUID(), userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());
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

        Order savedOrder = new Order(UUID.randomUUID(), userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        when(paymentGateway.initiate(savedOrder.id(), expectedTotal)).thenThrow(new RuntimeException("Gateway down"));

        assertThatThrownBy(() -> orderService.create(userId, addressId, slotId, slotDate, expectedTotal, idempotencyKey))
                .isInstanceOf(PaymentGatewayException.class)
                .hasFieldOrPropertyWithValue("orderId", savedOrder.id());
    }


    
    @Test
    void retryPayment_happyPath_registersSynchronizationAndSucceeds() {
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            UUID orderId = UUID.randomUUID();
            Order order = new Order(orderId, userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());

            when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

            com.builddash.backend.domain.model.Payment failedPayment = new com.builddash.backend.domain.model.Payment(
                UUID.randomUUID(), orderId, "old-tx", expectedTotal, com.builddash.backend.domain.enums.PaymentStatus.FAILED, "old-url"
            );
            when(paymentRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(failedPayment));

            com.builddash.backend.domain.model.Payment pendingPayment = new com.builddash.backend.domain.model.Payment(
                UUID.randomUUID(), orderId, null, expectedTotal, com.builddash.backend.domain.enums.PaymentStatus.PENDING, null
            );
            when(paymentRepository.save(any())).thenReturn(pendingPayment);

            OrderResponse response = orderService.retryPayment(userId, orderId);

            assertThat(response.id()).isEqualTo(orderId);
            assertThat(response.status()).isEqualTo("PAYMENT_PENDING");

            verify(paymentRepository, times(1)).save(any());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }


    
    @Test
    void retryPayment_whenOrderConfirmed_throwsInvalidOrderStateException() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.CONFIRMED, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.retryPayment(userId, orderId))
                .isInstanceOf(com.builddash.backend.domain.exception.InvalidOrderStateException.class)
                .hasFieldOrPropertyWithValue("code", "ORDER_ALREADY_CONFIRMED");
    }


    
    @Test
    void retryPayment_whenOrderCancelled_throwsInvalidOrderStateException() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.CANCELLED, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.retryPayment(userId, orderId))
                .isInstanceOf(com.builddash.backend.domain.exception.InvalidOrderStateException.class)
                .hasFieldOrPropertyWithValue("code", "ORDER_ALREADY_CANCELLED");
    }



    
    @Test
    void getOrder_happyPath_returnsOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.CONFIRMED, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrder(userId, orderId);

        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo("CONFIRMED");
    }

    @Test
    void getOrder_whenNotOwner_throwsNotFoundException() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, UUID.randomUUID(), addressId, slotId, slotDate, expectedTotal, OrderStatus.CONFIRMED, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrder(userId, orderId))
                .isInstanceOf(com.builddash.backend.domain.exception.NotFoundException.class);
    }


    
    @Test
    void listOrders_happyPath_returnsOrderList() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.CONFIRMED, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());
        when(orderRepository.findAllByUserId(userId)).thenReturn(List.of(order));

        List<OrderResponse> responses = orderService.listOrders(userId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(orderId);
    }


    
    @Test
    void reorder_happyPath_clearsCartAndUpsertsItems() {
        UUID orderId = UUID.randomUUID();
        OrderLineItem item = new OrderLineItem(UUID.randomUUID(), UUID.randomUUID(), 2, new BigDecimal("50.00"), BigDecimal.ZERO);
        Order order = new Order(orderId, userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.DELIVERED, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of(item));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        PricedCart newCart = mock(PricedCart.class);
        when(cartPricingCalculator.calculate(any(), eq(userId))).thenReturn(newCart);

        com.builddash.backend.api.dto.response.PricedCartResponse responseDto = mock(com.builddash.backend.api.dto.response.PricedCartResponse.class);
        when(cartDtoMapper.toResponse(newCart)).thenReturn(responseDto);
        when(responseDto.finalTotal()).thenReturn(new BigDecimal("110.00")); // re-priced total

        com.builddash.backend.api.dto.response.PricedCartResponse response = orderService.reorder(userId, orderId);

        assertThat(response.finalTotal()).isEqualTo(new BigDecimal("110.00"));
        assertThat(response.finalTotal()).isNotEqualTo(expectedTotal); // Explicitly assert re-pricing changed the total
    }

    
    @Test
    void retryPayment_whenPaymentAlreadyPending_throwsPaymentRetryInProgressException() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        com.builddash.backend.domain.model.Payment pendingPayment = new com.builddash.backend.domain.model.Payment(
            UUID.randomUUID(), orderId, null, expectedTotal, com.builddash.backend.domain.enums.PaymentStatus.PENDING, null
        );
        when(paymentRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(pendingPayment));

        assertThatThrownBy(() -> orderService.retryPayment(userId, orderId))
                .isInstanceOf(com.builddash.backend.domain.exception.PaymentRetryInProgressException.class);
    }
}
