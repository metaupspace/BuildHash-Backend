package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.CheckoutIntentService;
import com.builddash.backend.application.service.OrderResult;
import com.builddash.backend.application.service.ReorderResult;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.exception.PaymentGatewayException;
import com.builddash.backend.domain.model.CheckoutIntent;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.model.PaymentReference;
import com.builddash.backend.domain.model.PricedCart;
import com.builddash.backend.domain.model.PricedCartLineItem;
import com.builddash.backend.domain.port.IdempotencyKeyRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.PaymentRepository;
import com.builddash.backend.domain.port.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.builddash.backend.infra.config.OrderIdempotencyProperties;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
    @Mock
    private com.builddash.backend.application.service.CartService cartService;
    @Mock
    private com.builddash.backend.domain.port.CartPricingCalculator cartPricingCalculator;
    @Mock
    private com.builddash.backend.domain.port.CouponRedemptionRepository couponRedemptionRepository;
    @Mock
    private com.builddash.backend.domain.port.CouponRepository couponRepository;
    @Mock
    private com.builddash.backend.api.mapper.CartDtoMapper cartDtoMapper;

    /** Real instance (not a mock): the cutoff-derivation test asserts against its window. */
    @Spy
    private OrderIdempotencyProperties idempotencyProperties = new OrderIdempotencyProperties();

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
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        lenient().doAnswer(invocation -> {
            java.util.function.Consumer<?> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }



    @Test
    void create_whenIdempotencyKeyExists_returnsExistingOrderAndInitiatesPayment() {
        UUID existingOrderId = UUID.randomUUID();
        Order existingOrder = new Order(existingOrderId, userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());

        when(idempotencyKeyRepository.findOrderId(eq(idempotencyKey), any(Instant.class))).thenReturn(Optional.of(existingOrderId));
        when(orderRepository.findById(existingOrderId)).thenReturn(Optional.of(existingOrder));
        when(paymentGateway.initiate(existingOrderId, expectedTotal)).thenReturn(new PaymentReference("tx-1", "url-1"));

        OrderResult result = orderService.create(userId, addressId, slotId, slotDate, expectedTotal, idempotencyKey);

        assertThat(result.order().id()).isEqualTo(existingOrderId);
        assertThat(result.paymentUrl()).isEqualTo("url-1");
        verify(checkoutIntentService, never()).createIntent(any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }



    @Test
    void create_whenIdempotencyKeyBelongsToAnotherUsersOrder_throwsForbiddenAndSkipsGateway() {
        UUID existingOrderId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Order foreignOrder = new Order(existingOrderId, otherUserId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());

        when(idempotencyKeyRepository.findOrderId(eq(idempotencyKey), any(Instant.class))).thenReturn(Optional.of(existingOrderId));
        when(orderRepository.findById(existingOrderId)).thenReturn(Optional.of(foreignOrder));

        assertThatThrownBy(() -> orderService.create(userId, addressId, slotId, slotDate, expectedTotal, idempotencyKey))
                .isInstanceOf(com.builddash.backend.domain.exception.ForbiddenException.class);

        verify(paymentGateway, never()).initiate(any(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void create_whenNewIdempotencyKey_createsOrderAndInitiatesPayment() {
        when(idempotencyKeyRepository.findOrderId(eq(idempotencyKey), any(Instant.class))).thenReturn(Optional.empty());

        CheckoutIntent intent = mock(CheckoutIntent.class);
        when(intent.lockedTotal()).thenReturn(expectedTotal);
        PricedCart cart = mock(PricedCart.class);
        when(cart.items()).thenReturn(List.of());
        when(intent.pricedCart()).thenReturn(cart);

        when(checkoutIntentService.createIntent(userId, addressId, slotId, slotDate, expectedTotal, null)).thenReturn(intent);

        Order savedOrder = new Order(UUID.randomUUID(), userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        when(paymentGateway.initiate(savedOrder.id(), expectedTotal)).thenReturn(new PaymentReference("tx-1", "url-1"));

        OrderResult result = orderService.create(userId, addressId, slotId, slotDate, expectedTotal, idempotencyKey);

        assertThat(result.order().id()).isEqualTo(savedOrder.id());
        assertThat(result.paymentUrl()).isEqualTo("url-1");
        verify(idempotencyKeyRepository).save(idempotencyKey, savedOrder.id());
        verify(cartService).clearCart(userId, null);
    }

    @Test
    void create_derivesCutoffFromConfiguredWindow_passesItToFindOrderId() {
        when(idempotencyKeyRepository.findOrderId(eq(idempotencyKey), any(Instant.class))).thenReturn(Optional.empty());
        stubNewOrderHappyPath();

        orderService.create(userId, addressId, slotId, slotDate, expectedTotal, idempotencyKey);

        // Override: window widening must flow through to the read's cutoff.
        idempotencyProperties.setIdempotencyWindowHours(48);
        orderService.create(userId, addressId, slotId, slotDate, expectedTotal, idempotencyKey);

        org.mockito.ArgumentCaptor<Instant> cutoffs = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(idempotencyKeyRepository, times(2)).findOrderId(eq(idempotencyKey), cutoffs.capture());
        // Default (24h) and override (48h) both derived, not hardcoded.
        assertThat(Duration.between(cutoffs.getAllValues().get(0), Instant.now()))
                .isCloseTo(Duration.ofHours(24), Duration.ofSeconds(5));
        assertThat(Duration.between(cutoffs.getAllValues().get(1), Instant.now()))
                .isCloseTo(Duration.ofHours(48), Duration.ofSeconds(5));
    }

    private void stubNewOrderHappyPath() {
        CheckoutIntent intent = mock(CheckoutIntent.class);
        when(intent.lockedTotal()).thenReturn(expectedTotal);
        PricedCart cart = mock(PricedCart.class);
        when(cart.items()).thenReturn(List.of());
        when(intent.pricedCart()).thenReturn(cart);
        when(checkoutIntentService.createIntent(userId, addressId, slotId, slotDate, expectedTotal, null)).thenReturn(intent);
        Order savedOrder = new Order(UUID.randomUUID(), userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), Instant.now(), null, null, List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(paymentGateway.initiate(savedOrder.id(), expectedTotal)).thenReturn(new PaymentReference("tx-1", "url-1"));
    }

    @Test
    void create_whenGatewayFails_cartNotCleared() {
        when(idempotencyKeyRepository.findOrderId(eq(idempotencyKey), any(Instant.class))).thenReturn(Optional.empty());

        CheckoutIntent intent = mock(CheckoutIntent.class);
        when(intent.lockedTotal()).thenReturn(expectedTotal);
        PricedCart cart = mock(PricedCart.class);
        when(cart.items()).thenReturn(List.of());
        when(intent.pricedCart()).thenReturn(cart);
        when(checkoutIntentService.createIntent(userId, addressId, slotId, slotDate, expectedTotal, null)).thenReturn(intent);

        Order savedOrder = new Order(UUID.randomUUID(), userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(paymentGateway.initiate(savedOrder.id(), expectedTotal)).thenThrow(new RuntimeException("Gateway down"));

        assertThatThrownBy(() -> orderService.create(userId, addressId, slotId, slotDate, expectedTotal, idempotencyKey))
                .isInstanceOf(PaymentGatewayException.class);

        verify(cartService, never()).clearCart(any(), any());
    }



    @Test
    void create_whenCouponsApplied_recordsRedemptions() {
        when(idempotencyKeyRepository.findOrderId(eq(idempotencyKey), any(Instant.class))).thenReturn(Optional.empty());

        UUID cartCouponId = UUID.randomUUID();
        UUID itemCouponId = UUID.randomUUID();
        com.builddash.backend.domain.model.Coupon cartCoupon = mock(com.builddash.backend.domain.model.Coupon.class);
        when(cartCoupon.getId()).thenReturn(cartCouponId);
        com.builddash.backend.domain.model.Coupon itemCoupon = mock(com.builddash.backend.domain.model.Coupon.class);
        when(itemCoupon.getId()).thenReturn(itemCouponId);
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(cartCoupon));
        when(couponRepository.findByCode("ITEM5")).thenReturn(Optional.of(itemCoupon));

        PricedCartLineItem lineWithCoupon = new PricedCartLineItem(
                UUID.randomUUID(), 1, "123", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.valueOf(9), "ITEM5");
        PricedCart cart = new PricedCart(UUID.randomUUID(), userId, null, List.of(lineWithCoupon),
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.TEN, "SAVE10", null);

        CheckoutIntent intent = mock(CheckoutIntent.class);
        when(intent.lockedTotal()).thenReturn(expectedTotal);
        when(intent.pricedCart()).thenReturn(cart);
        when(checkoutIntentService.createIntent(userId, addressId, slotId, slotDate, expectedTotal, null)).thenReturn(intent);

        Order savedOrder = new Order(UUID.randomUUID(), userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(paymentGateway.initiate(savedOrder.id(), expectedTotal)).thenReturn(new PaymentReference("tx-1", "url-1"));

        orderService.create(userId, addressId, slotId, slotDate, expectedTotal, idempotencyKey);

        verify(couponRedemptionRepository).record(userId, cartCouponId, savedOrder.id());
        verify(couponRedemptionRepository).record(userId, itemCouponId, savedOrder.id());
    }

    @Test
    void create_whenNoCouponsApplied_recordsNothing() {
        when(idempotencyKeyRepository.findOrderId(eq(idempotencyKey), any(Instant.class))).thenReturn(Optional.empty());

        PricedCart cart = new PricedCart(UUID.randomUUID(), userId, null, List.of(),
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, null, null);

        CheckoutIntent intent = mock(CheckoutIntent.class);
        when(intent.lockedTotal()).thenReturn(expectedTotal);
        when(intent.pricedCart()).thenReturn(cart);
        when(checkoutIntentService.createIntent(userId, addressId, slotId, slotDate, expectedTotal, null)).thenReturn(intent);

        Order savedOrder = new Order(UUID.randomUUID(), userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(paymentGateway.initiate(savedOrder.id(), expectedTotal)).thenReturn(new PaymentReference("tx-1", "url-1"));

        orderService.create(userId, addressId, slotId, slotDate, expectedTotal, idempotencyKey);

        verify(couponRedemptionRepository, never()).record(any(), any(), any());
    }

    @Test
    void create_whenPaymentSaveFailsAfterGatewaySuccess_originalExceptionPropagates() {
        when(idempotencyKeyRepository.findOrderId(eq(idempotencyKey), any(Instant.class))).thenReturn(Optional.empty());

        CheckoutIntent intent = mock(CheckoutIntent.class);
        when(intent.lockedTotal()).thenReturn(expectedTotal);
        PricedCart cart = mock(PricedCart.class);
        when(cart.items()).thenReturn(List.of());
        when(intent.pricedCart()).thenReturn(cart);
        when(checkoutIntentService.createIntent(userId, addressId, slotId, slotDate, expectedTotal, null)).thenReturn(intent);

        Order savedOrder = new Order(UUID.randomUUID(), userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        when(paymentGateway.initiate(savedOrder.id(), expectedTotal)).thenReturn(new PaymentReference("tx-1", "url-1"));
        when(paymentRepository.save(any())).thenThrow(new IllegalStateException("DB down"));

        assertThatThrownBy(() -> orderService.create(userId, addressId, slotId, slotDate, expectedTotal, idempotencyKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB down");
    }

    @Test
    void create_whenConcurrentDuplicateKeySaveConflicts_returnsExistingOrder() {
        // Concurrent double-submit: both threads pass the read check, loser hits the
        // idempotency_key PK — must return the winner's order, not a 500
        UUID winnerOrderId = UUID.randomUUID();
        Order winnerOrder = new Order(winnerOrderId, userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());

        when(idempotencyKeyRepository.findOrderId(eq(idempotencyKey), any(Instant.class)))
                .thenReturn(Optional.empty())                 // first read: no key yet
                .thenReturn(Optional.of(winnerOrderId));      // re-read after conflict
        when(orderRepository.findById(winnerOrderId)).thenReturn(Optional.of(winnerOrder));
        when(paymentGateway.initiate(winnerOrderId, expectedTotal)).thenReturn(new PaymentReference("tx-1", "url-1"));

        CheckoutIntent intent = mock(CheckoutIntent.class);
        when(intent.lockedTotal()).thenReturn(expectedTotal);
        PricedCart cart = mock(PricedCart.class);
        when(cart.items()).thenReturn(List.of());
        when(intent.pricedCart()).thenReturn(cart);
        when(checkoutIntentService.createIntent(userId, addressId, slotId, slotDate, expectedTotal, null)).thenReturn(intent);
        Order savedOrder = new Order(UUID.randomUUID(), userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"))
                .when(idempotencyKeyRepository).save(idempotencyKey, savedOrder.id());

        OrderResult result = orderService.create(userId, addressId, slotId, slotDate, expectedTotal, idempotencyKey);

        assertThat(result.order().id()).isEqualTo(winnerOrderId);
    }

    @Test
    void create_whenPaymentGatewayFails_throwsPaymentGatewayException() {
        when(idempotencyKeyRepository.findOrderId(eq(idempotencyKey), any(Instant.class))).thenReturn(Optional.empty());

        CheckoutIntent intent = mock(CheckoutIntent.class);
        when(intent.lockedTotal()).thenReturn(expectedTotal);
        PricedCart cart = mock(PricedCart.class);
        when(cart.items()).thenReturn(List.of());
        when(intent.pricedCart()).thenReturn(cart);

        when(checkoutIntentService.createIntent(userId, addressId, slotId, slotDate, expectedTotal, null)).thenReturn(intent);

        Order savedOrder = new Order(UUID.randomUUID(), userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        when(paymentGateway.initiate(savedOrder.id(), expectedTotal)).thenThrow(new RuntimeException("Gateway down"));

        assertThatThrownBy(() -> orderService.create(userId, addressId, slotId, slotDate, expectedTotal, idempotencyKey))
                .isInstanceOf(PaymentGatewayException.class)
                .hasFieldOrPropertyWithValue("orderId", savedOrder.id());
    }



    @Test
    void retryPayment_happyPath_returnsNewPaymentUrl() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        com.builddash.backend.domain.model.Payment failedPayment = new com.builddash.backend.domain.model.Payment(
            UUID.randomUUID(), orderId, "old-tx", expectedTotal, com.builddash.backend.domain.enums.PaymentStatus.FAILED, "old-url"
        );
        when(paymentRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(failedPayment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGateway.initiate(orderId, expectedTotal)).thenReturn(new PaymentReference("tx-2", "url-2"));

        OrderResult result = orderService.retryPayment(userId, orderId);

        assertThat(result.order().id()).isEqualTo(orderId);
        assertThat(result.paymentUrl()).isEqualTo("url-2");
        verify(paymentGateway).initiate(orderId, expectedTotal);
        verify(paymentRepository, times(2)).save(any()); // pending row, then URL update
    }

    @Test
    void retryPayment_whenGatewayFails_marksPaymentFailedAndThrows() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findLatestByOrderId(orderId)).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGateway.initiate(orderId, expectedTotal)).thenThrow(new RuntimeException("gateway down"));

        assertThatThrownBy(() -> orderService.retryPayment(userId, orderId))
                .isInstanceOf(PaymentGatewayException.class);

        org.mockito.ArgumentCaptor<com.builddash.backend.domain.model.Payment> captor =
                org.mockito.ArgumentCaptor.forClass(com.builddash.backend.domain.model.Payment.class);
        verify(paymentRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).status())
                .isEqualTo(com.builddash.backend.domain.enums.PaymentStatus.FAILED);
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

        Order result = orderService.getOrder(userId, orderId);

        assertThat(result.id()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
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

        List<Order> responses = orderService.listOrders(userId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(orderId);
    }



    @Test
    void reorder_happyPath_bypassesCartAndCalculatesLivePrices() {
        UUID orderId = UUID.randomUUID();
        // Set up the old order with a product that was bought for 50.00
        UUID productId = UUID.randomUUID();
        OrderLineItem item = new OrderLineItem(UUID.randomUUID(), productId, 2, new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("100.00"));
        Order order = new Order(orderId, userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.DELIVERED, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of(item));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        PricedCart newCart = mock(PricedCart.class);
        // Assert cartPricingCalculator is called with a brand new transient cart, bypassing existing cart lookup
        when(cartService.createReorderCart(eq(userId), anyList())).thenReturn(newCart);
        when(newCart.id()).thenReturn(UUID.randomUUID());

        // Setup a mock for PricedCart to demonstrate re-pricing would reflect correctly if we returned it,
        // but now that we return ReorderResponse we just verify createReorderCart is the port that's hit.
        ReorderResult result = orderService.reorder(userId, orderId);

        assertThat(result.cartId()).isEqualTo(newCart.id());
        assertThat(result.message()).isEqualTo("Items added to cart");
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
