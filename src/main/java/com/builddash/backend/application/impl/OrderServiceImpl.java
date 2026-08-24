package com.builddash.backend.application.impl;

import com.builddash.backend.api.dto.response.OrderResponse;
import com.builddash.backend.application.service.CheckoutIntentService;
import com.builddash.backend.application.service.OrderService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.exception.PaymentGatewayException;
import com.builddash.backend.domain.model.CheckoutIntent;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.model.PaymentReference;
import com.builddash.backend.domain.port.IdempotencyKeyRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.builddash.backend.api.dto.response.OrderLineItemResponse;
import com.builddash.backend.api.dto.response.ReorderResponse;
import com.builddash.backend.application.service.CartService;
import com.builddash.backend.domain.model.CartLineItem;
import java.util.stream.Collectors;

import com.builddash.backend.domain.exception.InvalidOrderStateException;
import com.builddash.backend.domain.exception.PaymentRetryInProgressException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final CheckoutIntentService checkoutIntentService;
    private final OrderRepository orderRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final com.builddash.backend.domain.port.PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;
    private final CartService cartService;

    @Override
    public OrderResponse create(UUID userId, UUID addressId, UUID slotId, LocalDate slotDate, BigDecimal expectedTotal, String idempotencyKey) {
        Order savedOrder = transactionTemplate.execute(status -> {
            Optional<UUID> existingOrderId = idempotencyKeyRepository.findOrderId(idempotencyKey);
            if (existingOrderId.isPresent()) {
                return orderRepository.findById(existingOrderId.get())
                        .orElseThrow(() -> new IllegalStateException("Order not found for idempotency key"));
            }

            CheckoutIntent intent = checkoutIntentService.createIntent(userId, addressId, slotId, slotDate, expectedTotal, null);

            List<OrderLineItem> lineItems = intent.pricedCart().items().stream()
                    .map(item -> new OrderLineItem(
                            UUID.randomUUID(),
                            item.productId(),
                            item.quantity(),
                            item.unitFinalPrice(),
                            item.lineGst()
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
            idempotencyKeyRepository.save(idempotencyKey, saved.id());

            return saved;
        });

        PaymentReference ref;
        try {
            ref = paymentGateway.initiate(savedOrder.id(), savedOrder.totalAmount());

            com.builddash.backend.domain.model.Payment payment = new com.builddash.backend.domain.model.Payment(
                    UUID.randomUUID(),
                    savedOrder.id(),
                    ref.transactionId(),
                    savedOrder.totalAmount(),
                    com.builddash.backend.domain.enums.PaymentStatus.PENDING,
                    ref.paymentUrl()
            );
            paymentRepository.save(payment);

        } catch (Exception e) {
            throw new PaymentGatewayException(savedOrder.id(), e.getMessage());
        }

        return mapToResponse(savedOrder, ref.paymentUrl());
    }

    private OrderResponse mapToResponse(Order order, String paymentUrl) {
        List<OrderLineItemResponse> items = order.lineItems().stream()
                .map(item -> new OrderLineItemResponse(item.productId(), item.quantity(), item.unitPrice(), item.taxAmount()))
                .toList();

        return new OrderResponse(
                order.id(),
                order.status().name(),
                order.totalAmount(),
                paymentUrl,
                order.placedAt(),
                order.driverId(),
                order.driverPhone(),
                items
        );
    }

    @Override
    @Transactional
    public OrderResponse retryPayment(UUID userId, UUID orderId) {
        // Find by ID for update to lock the row against sweep/webhook races
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new com.builddash.backend.domain.exception.NotFoundException("Order", orderId.toString()));

        if (!order.userId().equals(userId)) {
            throw new com.builddash.backend.domain.exception.ForbiddenException("FORBIDDEN", "User does not own this order");
        }

        // Verify PAYMENT_PENDING under lock
        if (order.status() != OrderStatus.PAYMENT_PENDING) {
            throw new InvalidOrderStateException(order.status());
        }

        // Verify latest payment status
        Optional<com.builddash.backend.domain.model.Payment> latestPayment = paymentRepository.findLatestByOrderId(orderId);
        if (latestPayment.isPresent() && latestPayment.get().status() == com.builddash.backend.domain.enums.PaymentStatus.PENDING) {
            throw new PaymentRetryInProgressException();
        }

        // Create new pending payment row
        com.builddash.backend.domain.model.Payment newPayment = new com.builddash.backend.domain.model.Payment(
                UUID.randomUUID(),
                order.id(),
                null,
                order.totalAmount(),
                com.builddash.backend.domain.enums.PaymentStatus.PENDING,
                null
        );
        com.builddash.backend.domain.model.Payment savedPayment = paymentRepository.save(newPayment);

        // Register gateway.initiate() via afterCommit
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    PaymentReference ref = paymentGateway.initiate(order.id(), order.totalAmount());
                    com.builddash.backend.domain.model.Payment updated = new com.builddash.backend.domain.model.Payment(
                            savedPayment.id(),
                            order.id(),
                            ref.transactionId(),
                            order.totalAmount(),
                            com.builddash.backend.domain.enums.PaymentStatus.PENDING,
                            ref.paymentUrl()
                    );
                    paymentRepository.save(updated);
                } catch (Exception e) {
                    // Gateway failed; mark payment as failed.
                    com.builddash.backend.domain.model.Payment failed = new com.builddash.backend.domain.model.Payment(
                            savedPayment.id(),
                            order.id(),
                            null,
                            order.totalAmount(),
                            com.builddash.backend.domain.enums.PaymentStatus.FAILED,
                            null
                    );
                    paymentRepository.save(failed);
                }
            }
        });

        return mapToResponse(order, null);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID userId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.userId().equals(userId))
                .orElseThrow(() -> new com.builddash.backend.domain.exception.NotFoundException("Order", orderId.toString()));

        return mapToResponse(order, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(UUID userId) {
        return orderRepository.findAllByUserId(userId).stream()
                .map(order -> mapToResponse(order, null))
                .toList();
    }

    @Override
    @Transactional
    public ReorderResponse reorder(UUID userId, UUID orderId) {
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

        return new ReorderResponse(pricedCart.id(), "Items added to cart");
    }
}
