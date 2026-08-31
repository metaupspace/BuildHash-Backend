package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.CheckoutIntentService;
import com.builddash.backend.application.service.OrderService;
import com.builddash.backend.application.service.OrderResult;
import com.builddash.backend.application.service.ReorderResult;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.exception.PaymentGatewayException;
import com.builddash.backend.domain.model.CheckoutIntent;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.model.PaymentReference;
import com.builddash.backend.domain.port.IdempotencyKeyRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.PaymentGateway;
import com.builddash.backend.infra.config.OrderIdempotencyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.builddash.backend.application.service.CartService;
import com.builddash.backend.domain.model.CartLineItem;
import java.util.stream.Collectors;

import com.builddash.backend.domain.exception.InvalidOrderStateException;
import com.builddash.backend.domain.exception.PaymentRetryInProgressException;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final CheckoutIntentService checkoutIntentService;
    private final OrderRepository orderRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OrderIdempotencyProperties idempotencyProperties;
    private final com.builddash.backend.domain.port.PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;
    private final CartService cartService;
    private final com.builddash.backend.domain.port.CouponRepository couponRepository;
    private final com.builddash.backend.domain.port.CouponRedemptionRepository couponRedemptionRepository;

    @Override
    public OrderResult create(UUID userId, UUID addressId, UUID slotId, LocalDate slotDate, BigDecimal expectedTotal, String idempotencyKey) {
        Order savedOrder = transactionTemplate.execute(status -> {
            // Rolling window (PLAN_PHASE8 decision 10): an expired key reads as absent —
            // the caller gets a genuinely new order, not the stale one.
            Instant idempotencyCutoff = Instant.now()
                    .minus(Duration.ofHours(idempotencyProperties.getIdempotencyWindowHours()));
            Optional<UUID> existingOrderId = idempotencyKeyRepository.findOrderId(idempotencyKey, idempotencyCutoff);
            if (existingOrderId.isPresent()) {
                return existingOrderForKey(idempotencyKey, userId);
            }

            CheckoutIntent intent = checkoutIntentService.createIntent(userId, addressId, slotId, slotDate, expectedTotal, null);

            List<OrderLineItem> lineItems = intent.pricedCart().items().stream()
                    .map(item -> new OrderLineItem(
                            UUID.randomUUID(),
                            item.productId(),
                            item.quantity(),
                            item.unitFinalPrice(),
                            item.lineGst(),
                            item.lineFinalTotal()
                    )).collect(Collectors.toList());

            Order newOrder = new Order(
                    UUID.randomUUID(),
                    userId,
                    addressId,
                    slotId,
                    slotDate,
                    intent.lockedTotal(),
                    OrderStatus.PAYMENT_PENDING,
                    intent.deliverySlotLockId(),
                    java.time.Instant.now(),
                    null,
                    null,
                    lineItems
            );

            Order saved = orderRepository.save(newOrder);
            try {
                idempotencyKeyRepository.save(idempotencyKey, saved.id());
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Concurrent double-submit: this thread lost the PK race — the winner's
                // order already exists under this key, return it instead of surfacing 500
                return existingOrderForKey(idempotencyKey, userId);
            }
            recordCouponRedemptions(userId, intent.pricedCart(), saved.id());

            return saved;
        });

        // Only the gateway call maps to PaymentGatewayException — a DB failure after a
        // successful initiate must surface as itself, not be mislabeled "gateway down".
        final PaymentReference ref;
        try {
            ref = paymentGateway.initiate(savedOrder.id(), savedOrder.totalAmount());
        } catch (Exception e) {
            throw new PaymentGatewayException(savedOrder.id(), e.getMessage());
        }

        paymentRepository.save(new com.builddash.backend.domain.model.Payment(
                UUID.randomUUID(),
                savedOrder.id(),
                ref.transactionId(),
                savedOrder.totalAmount(),
                com.builddash.backend.domain.enums.PaymentStatus.PENDING,
                ref.paymentUrl()
        ));

        cartService.clearCart(userId, null);

        return new OrderResult(savedOrder, ref.paymentUrl());
    }

    private Order existingOrderForKey(String idempotencyKey, UUID userId) {
        // Race-retry path: the winner inserted this key moments ago, so the window filter
        // can never miss it — same cutoff discipline as the primary read for consistency.
        Instant idempotencyCutoff = Instant.now()
                .minus(Duration.ofHours(idempotencyProperties.getIdempotencyWindowHours()));
        UUID orderId = idempotencyKeyRepository.findOrderId(idempotencyKey, idempotencyCutoff)
                .orElseThrow(() -> new IllegalStateException("Order not found for idempotency key"));
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found for idempotency key"));
        if (!order.userId().equals(userId)) {
            throw new com.builddash.backend.domain.exception.ForbiddenException("FORBIDDEN", "User does not own this order");
        }
        return order;
    }

    /**
     * Persists one redemption row per applied coupon so maxUsesPerUser limits hold.
     * Records the requested code — item coupons count as used even if a later step
     * (e.g. margin floor) absorbed the discount; over-counting is the safe direction.
     */
    private void recordCouponRedemptions(UUID userId, com.builddash.backend.domain.model.PricedCart pricedCart, UUID orderId) {
        java.util.Set<String> codes = new java.util.LinkedHashSet<>();
        if (pricedCart.appliedCartCoupon() != null) {
            codes.add(pricedCart.appliedCartCoupon());
        }
        pricedCart.items().stream()
                .map(com.builddash.backend.domain.model.PricedCartLineItem::appliedItemCoupon)
                .filter(code -> code != null && !code.isBlank())
                .forEach(codes::add);

        for (String code : codes) {
            couponRepository.findByCode(code)
                    .ifPresent(coupon -> couponRedemptionRepository.record(userId, coupon.getId(), orderId));
        }
    }

    @Override
    public OrderResult retryPayment(UUID userId, UUID orderId) {
        // Tx 1: lock the order row against sweep/webhook races, validate, insert pending payment
        final Order[] orderHolder = new Order[1];
        com.builddash.backend.domain.model.Payment savedPayment = transactionTemplate.execute(status -> {
            Order order = orderRepository.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new com.builddash.backend.domain.exception.NotFoundException("Order", orderId.toString()));

            if (!order.userId().equals(userId)) {
                throw new com.builddash.backend.domain.exception.ForbiddenException("FORBIDDEN", "User does not own this order");
            }
            if (order.status() != OrderStatus.PAYMENT_PENDING) {
                throw new InvalidOrderStateException(order.status());
            }
            Optional<com.builddash.backend.domain.model.Payment> latestPayment = paymentRepository.findLatestByOrderId(orderId);
            if (latestPayment.isPresent() && latestPayment.get().status() == com.builddash.backend.domain.enums.PaymentStatus.PENDING) {
                throw new PaymentRetryInProgressException();
            }

            orderHolder[0] = order;
            return paymentRepository.save(new com.builddash.backend.domain.model.Payment(
                    UUID.randomUUID(), order.id(), null, order.totalAmount(),
                    com.builddash.backend.domain.enums.PaymentStatus.PENDING, null
            ));
        });
        Order order = orderHolder[0];

        // Gateway outside the tx so the row lock isn't held during the network call
        final PaymentReference ref;
        try {
            ref = paymentGateway.initiate(orderId, order.totalAmount());
        } catch (Exception e) {
            transactionTemplate.executeWithoutResult(status -> paymentRepository.save(
                    new com.builddash.backend.domain.model.Payment(
                            savedPayment.id(), orderId, null, order.totalAmount(),
                            com.builddash.backend.domain.enums.PaymentStatus.FAILED, null)));
            throw new PaymentGatewayException(orderId, e.getMessage());
        }

        // Tx 2: record transaction id + payment URL so the client can actually pay
        transactionTemplate.executeWithoutResult(status -> paymentRepository.save(
                new com.builddash.backend.domain.model.Payment(
                        savedPayment.id(), orderId, ref.transactionId(), order.totalAmount(),
                        com.builddash.backend.domain.enums.PaymentStatus.PENDING, ref.paymentUrl())));

        return new OrderResult(order, ref.paymentUrl());
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrder(UUID userId, UUID orderId) {
        return orderRepository.findById(orderId)
                .filter(o -> o.userId().equals(userId))
                .orElseThrow(() -> new com.builddash.backend.domain.exception.NotFoundException("Order", orderId.toString()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> listOrders(UUID userId) {
        return orderRepository.findAllByUserId(userId);
    }

    @Override
    @Transactional
    public ReorderResult reorder(UUID userId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.userId().equals(userId))
                .orElseThrow(() -> new com.builddash.backend.domain.exception.NotFoundException("Order", orderId.toString()));

        List<CartLineItem> cartItems = order.lineItems().stream()
                .map(li -> new CartLineItem(
                        UUID.randomUUID(),
                        null,
                        li.productId(),
                        li.quantity(),
                        null
                ))
                .toList();

        com.builddash.backend.domain.model.PricedCart pricedCart = cartService.createReorderCart(userId, cartItems);

        return new ReorderResult(pricedCart.id(), "Items added to cart");
    }
}
