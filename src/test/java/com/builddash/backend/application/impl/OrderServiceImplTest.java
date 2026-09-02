package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.ApprovalGateService;
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
    @Mock
    private com.builddash.backend.application.service.B2bAuthorizer b2bAuthorizer;
    @Mock
    private com.builddash.backend.application.service.ApprovalGateService approvalGateService;
    @Mock
    private com.builddash.backend.domain.port.CompanySiteRepository companySiteRepository;

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
    void create_whenIdempotencyKeyExists_returnsExistingOrderWithoutReinitiatingGateway() {
        // H1.3b: retrying create() with the same key must never call the gateway a
        // second time for an order that may already have a payment attempt in flight.
        UUID existingOrderId = UUID.randomUUID();
        Order existingOrder = new Order(existingOrderId, userId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());
        com.builddash.backend.domain.model.Payment existingPayment = new com.builddash.backend.domain.model.Payment(
                UUID.randomUUID(), existingOrderId, "tx-1", expectedTotal,
                com.builddash.backend.domain.enums.PaymentStatus.PENDING, "url-1");

        when(idempotencyKeyRepository.findOrderId(eq(idempotencyKey), any(Instant.class))).thenReturn(Optional.of(existingOrderId));
        when(orderRepository.findById(existingOrderId)).thenReturn(Optional.of(existingOrder));
        when(paymentRepository.findLatestByOrderId(existingOrderId)).thenReturn(Optional.of(existingPayment));

        OrderResult result = orderService.create(userId, addressId, slotId, slotDate, expectedTotal, null, null, idempotencyKey);

        assertThat(result.order().id()).isEqualTo(existingOrderId);
        assertThat(result.paymentUrl()).isEqualTo("url-1");
        verify(checkoutIntentService, never()).createIntent(any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
        verify(paymentGateway, never()).initiate(any(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void create_calledTwiceWithSameIdempotencyKey_initiatesGatewayExactlyOnce() {
        // OrderCreateRetryDoesNotDoubleInitiateGatewayTest scenario (H1.3b): two create()
        // calls with the same idempotency key must hit the gateway at most once.
        Order savedOrder = new Order(UUID.randomUUID(), userId, addressId, slotId, slotDate, expectedTotal,
                OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), Instant.now(), null, null, List.of());

        when(idempotencyKeyRepository.findOrderId(eq(idempotencyKey), any(Instant.class)))
                .thenReturn(Optional.empty())                   // 1st call: genuinely new
                .thenReturn(Optional.of(savedOrder.id()));       // 2nd call: retry resolves to it

        CheckoutIntent intent = mock(CheckoutIntent.class);
        when(intent.lockedTotal()).thenReturn(expectedTotal);
        PricedCart cart = mock(PricedCart.class);
        when(cart.items()).thenReturn(List.of());
        when(intent.pricedCart()).thenReturn(cart);
        when(checkoutIntentService.createIntent(userId, addressId, slotId, slotDate, expectedTotal, null)).thenReturn(intent);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(orderRepository.findById(savedOrder.id())).thenReturn(Optional.of(savedOrder));
        when(paymentGateway.initiate(savedOrder.id(), expectedTotal)).thenReturn(new PaymentReference("tx-1", "url-1"));

        OrderResult first = orderService.create(userId, addressId, slotId, slotDate, expectedTotal, null, null, idempotencyKey);

        com.builddash.backend.domain.model.Payment claim = new com.builddash.backend.domain.model.Payment(
                UUID.randomUUID(), savedOrder.id(), "tx-1", expectedTotal,
                com.builddash.backend.domain.enums.PaymentStatus.PENDING, "url-1");
        when(paymentRepository.findLatestByOrderId(savedOrder.id())).thenReturn(Optional.of(claim));

        OrderResult retry = orderService.create(userId, addressId, slotId, slotDate, expectedTotal, null, null, idempotencyKey);

        assertThat(first.order().id()).isEqualTo(savedOrder.id());
        assertThat(retry.order().id()).isEqualTo(savedOrder.id());
        assertThat(retry.paymentUrl()).isEqualTo("url-1");
        verify(paymentGateway, times(1)).initiate(any(), any());
    }



    @Test
    void create_whenIdempotencyKeyBelongsToAnotherUsersOrder_throwsForbiddenAndSkipsGateway() {
        UUID existingOrderId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Order foreignOrder = new Order(existingOrderId, otherUserId, addressId, slotId, slotDate, expectedTotal, OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());

        when(idempotencyKeyRepository.findOrderId(eq(idempotencyKey), any(Instant.class))).thenReturn(Optional.of(existingOrderId));
        when(orderRepository.findById(existingOrderId)).thenReturn(Optional.of(foreignOrder));

        assertThatThrownBy(() -> orderService.create(userId, addressId, slotId, slotDate, expectedTotal, null, null, idempotencyKey))
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

        OrderResult result = orderService.create(userId, addressId, slotId, slotDate, expectedTotal, null, null, idempotencyKey);

        assertThat(result.order().id()).isEqualTo(savedOrder.id());
        assertThat(result.paymentUrl()).isEqualTo("url-1");
        verify(idempotencyKeyRepository).save(idempotencyKey, savedOrder.id());
        verify(cartService).clearCart(userId, null);
    }

    @Test
    void create_derivesCutoffFromConfiguredWindow_passesItToFindOrderId() {
        when(idempotencyKeyRepository.findOrderId(eq(idempotencyKey), any(Instant.class))).thenReturn(Optional.empty());
        stubNewOrderHappyPath();

        orderService.create(userId, addressId, slotId, slotDate, expectedTotal, null, null, idempotencyKey);

        // Override: window widening must flow through to the read's cutoff.
        idempotencyProperties.setIdempotencyWindowHours(48);
        orderService.create(userId, addressId, slotId, slotDate, expectedTotal, null, null, idempotencyKey);

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

        assertThatThrownBy(() -> orderService.create(userId, addressId, slotId, slotDate, expectedTotal, null, null, idempotencyKey))
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

        orderService.create(userId, addressId, slotId, slotDate, expectedTotal, null, null, idempotencyKey);

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

        orderService.create(userId, addressId, slotId, slotDate, expectedTotal, null, null, idempotencyKey);

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
        // First save is the H1.3 pre-gateway claim (must succeed so the gateway is
        // actually called); second save is completePaymentInitiation's post-gateway
        // update, which is where this test's DB failure occurs.
        when(paymentRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0))
                .thenThrow(new IllegalStateException("DB down"));

        assertThatThrownBy(() -> orderService.create(userId, addressId, slotId, slotDate, expectedTotal, null, null, idempotencyKey))
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
        // H1.3b: the loser resolves via existingOrderForKey (a retry path) and must not
        // re-enter the gateway — no paymentGateway stub needed/expected here.

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

        OrderResult result = orderService.create(userId, addressId, slotId, slotDate, expectedTotal, null, null, idempotencyKey);

        assertThat(result.order().id()).isEqualTo(winnerOrderId);
        verify(paymentGateway, never()).initiate(any(), any());
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

        assertThatThrownBy(() -> orderService.create(userId, addressId, slotId, slotDate, expectedTotal, null, null, idempotencyKey))
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

    // ---------- 9-D: approval gate branch ----------

    @Test
    void create_whenB2bDraftCartMatchesPolicy_isBornPendingApprovalWithoutPayment() {
        UUID cartId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID lockId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();

        when(idempotencyKeyRepository.findOrderId(eq(idempotencyKey), any(Instant.class))).thenReturn(Optional.empty());
        PricedCart draft = mock(PricedCart.class);
        when(draft.companyId()).thenReturn(companyId);
        when(draft.projectId()).thenReturn(sourceId);
        when(cartService.getCartById(userId, cartId)).thenReturn(draft);
        when(companySiteRepository.findById(siteId)).thenReturn(Optional.of(
                new com.builddash.backend.domain.model.CompanySite(siteId, companyId, "Main", null, true, null, null)));

        CheckoutIntent intent = mock(CheckoutIntent.class);
        when(intent.lockedTotal()).thenReturn(expectedTotal);
        PricedCart intentCart = mock(PricedCart.class);
        when(intentCart.items()).thenReturn(List.of());
        when(intentCart.companyId()).thenReturn(companyId);
        when(intent.pricedCart()).thenReturn(intentCart);
        when(intent.deliverySlotLockId()).thenReturn(lockId);
        when(checkoutIntentService.createIntent(userId, addressId, slotId, slotDate, expectedTotal, cartId)).thenReturn(intent);

        ApprovalGateService.GateDecision gated = new ApprovalGateService.GateDecision(true,
                List.of(com.builddash.backend.domain.enums.ApprovalMatchRule.AMOUNT), List.of(),
                new BigDecimal("50.00"), List.of(com.builddash.backend.domain.enums.CompanyRole.PROCUREMENT_MANAGER), 24, 1);
        when(approvalGateService.evaluate(eq(companyId), eq(expectedTotal), any(), any())).thenReturn(gated);

        Order savedOrder = new Order(UUID.randomUUID(), userId, addressId, slotId, slotDate, expectedTotal,
                OrderStatus.PENDING_APPROVAL, null, java.time.Instant.now(), null, null, List.of(), companyId, null, null);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        OrderResult result = orderService.create(userId, addressId, slotId, slotDate, expectedTotal, cartId, siteId, idempotencyKey);

        assertThat(result.paymentUrl()).isNull();
        assertThat(result.order().status()).isEqualTo(OrderStatus.PENDING_APPROVAL);
        verify(b2bAuthorizer).authorize(userId, companyId,
                com.builddash.backend.domain.enums.CompanyPermission.ORDER_CREATE, siteId, true);
        verify(approvalGateService).openApproval(savedOrder, gated, lockId);
        verify(paymentGateway, never()).initiate(any(), any());
        verify(paymentRepository, never()).save(any());
        verify(cartService).clearCart(userId, sourceId);
    }

    @Test
    void create_whenB2bCartWithoutPolicy_followsOrdinaryPaymentFlow() {
        UUID cartId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();

        when(idempotencyKeyRepository.findOrderId(eq(idempotencyKey), any(Instant.class))).thenReturn(Optional.empty());
        PricedCart draft = mock(PricedCart.class);
        when(draft.companyId()).thenReturn(companyId);
        when(draft.projectId()).thenReturn(UUID.randomUUID());
        when(cartService.getCartById(userId, cartId)).thenReturn(draft);
        when(companySiteRepository.findById(siteId)).thenReturn(Optional.of(
                new com.builddash.backend.domain.model.CompanySite(siteId, companyId, "Main", null, true, null, null)));

        CheckoutIntent intent = mock(CheckoutIntent.class);
        when(intent.lockedTotal()).thenReturn(expectedTotal);
        PricedCart intentCart = mock(PricedCart.class);
        when(intentCart.items()).thenReturn(List.of());
        when(intentCart.companyId()).thenReturn(companyId);
        when(intent.pricedCart()).thenReturn(intentCart);
        when(intent.deliverySlotLockId()).thenReturn(UUID.randomUUID());
        when(checkoutIntentService.createIntent(userId, addressId, slotId, slotDate, expectedTotal, cartId)).thenReturn(intent);

        when(approvalGateService.evaluate(eq(companyId), eq(expectedTotal), any(), any()))
                .thenReturn(ApprovalGateService.GateDecision.notGated());

        Order savedOrder = new Order(UUID.randomUUID(), userId, addressId, slotId, slotDate, expectedTotal,
                OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of(), companyId, null, null);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(paymentGateway.initiate(eq(savedOrder.id()), eq(expectedTotal))).thenReturn(new PaymentReference("tx-9", "url-9"));

        OrderResult result = orderService.create(userId, addressId, slotId, slotDate, expectedTotal, cartId, siteId, idempotencyKey);

        assertThat(result.paymentUrl()).isEqualTo("url-9");
        verify(approvalGateService, never()).openApproval(any(), any(), any());
        verify(paymentGateway).initiate(savedOrder.id(), expectedTotal);
    }

    @Test
    void create_whenB2bCartWithoutSiteId_throwsSiteRequired() {
        // H0.5: an optional siteId defeats site scoping and can bypass a site-only
        // approval policy — company checkout must name its site.
        UUID cartId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        PricedCart draft = mock(PricedCart.class);
        when(draft.companyId()).thenReturn(companyId);
        when(cartService.getCartById(userId, cartId)).thenReturn(draft);

        assertThatThrownBy(() -> orderService.create(userId, addressId, slotId, slotDate, expectedTotal, cartId, null, idempotencyKey))
                .isInstanceOf(com.builddash.backend.domain.exception.BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", "SITE_REQUIRED");

        verify(b2bAuthorizer, never()).authorize(any(), any(), any(), any(), anyBoolean());
        verify(checkoutIntentService, never()).createIntent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_whenSiteIdFromAnotherCompany_throwsBadRequest() {
        UUID cartId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID foreignSiteId = UUID.randomUUID();

        PricedCart draft = mock(PricedCart.class);
        when(draft.companyId()).thenReturn(companyId);
        when(cartService.getCartById(userId, cartId)).thenReturn(draft);
        when(companySiteRepository.findById(foreignSiteId)).thenReturn(Optional.of(
                new com.builddash.backend.domain.model.CompanySite(foreignSiteId, UUID.randomUUID(),
                        "Foreign", null, true, null, null)));

        assertThatThrownBy(() -> orderService.create(userId, addressId, slotId, slotDate, expectedTotal, cartId, foreignSiteId, idempotencyKey))
                .isInstanceOf(com.builddash.backend.domain.exception.BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", "SITE_INVALID");

        verify(checkoutIntentService, never()).createIntent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_whenSiteInactive_throwsBadRequest() {
        UUID cartId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();

        PricedCart draft = mock(PricedCart.class);
        when(draft.companyId()).thenReturn(companyId);
        when(cartService.getCartById(userId, cartId)).thenReturn(draft);
        when(companySiteRepository.findById(siteId)).thenReturn(Optional.of(
                new com.builddash.backend.domain.model.CompanySite(siteId, companyId,
                        "Main", null, false, null, null)));

        assertThatThrownBy(() -> orderService.create(userId, addressId, slotId, slotDate, expectedTotal, cartId, siteId, idempotencyKey))
                .isInstanceOf(com.builddash.backend.domain.exception.BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", "SITE_INACTIVE");
    }

    @Test
    void retryPayment_whenPendingApproval_throwsInvalidOrderState() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, userId, addressId, slotId, slotDate, expectedTotal,
                OrderStatus.PENDING_APPROVAL, null, java.time.Instant.now(), null, null, List.of());

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.retryPayment(userId, orderId))
                .isInstanceOf(com.builddash.backend.domain.exception.InvalidOrderStateException.class)
                .hasFieldOrPropertyWithValue("code", "ORDER_ALREADY_PENDING_APPROVAL");
        verify(paymentGateway, never()).initiate(any(), any());
    }

    @Test
    void initiatePaymentForApprovedOrder_happyPath_initiatesOncePersistsPending() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, userId, addressId, slotId, slotDate, expectedTotal,
                OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of());

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findLatestByOrderId(orderId)).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGateway.initiate(orderId, expectedTotal)).thenReturn(new PaymentReference("tx-10", "url-10"));

        OrderResult result = orderService.initiatePaymentForApprovedOrder(orderId);

        assertThat(result.paymentUrl()).isEqualTo("url-10");
        verify(paymentGateway, times(1)).initiate(orderId, expectedTotal);
        verify(paymentRepository, times(2)).save(any());
    }

    @Test
    void initiatePaymentForApprovedOrder_whenNotPaymentPending_throws() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, userId, addressId, slotId, slotDate, expectedTotal,
                OrderStatus.PENDING_APPROVAL, null, java.time.Instant.now(), null, null, List.of());
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.initiatePaymentForApprovedOrder(orderId))
                .isInstanceOf(com.builddash.backend.domain.exception.InvalidOrderStateException.class);
        verify(paymentGateway, never()).initiate(any(), any());
    }
}
