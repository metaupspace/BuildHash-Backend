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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final CheckoutIntentService checkoutIntentService;
    private final OrderRepository orderRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;

    @Override
    public OrderResponse create(UUID userId, UUID addressId, UUID slotId, LocalDate slotDate, BigDecimal expectedTotal, String idempotencyKey) {
        Order savedOrder = transactionTemplate.execute(status -> {
            Optional<UUID> existingOrderId = idempotencyKeyRepository.findOrderId(idempotencyKey);
            if (existingOrderId.isPresent()) {
                return orderRepository.findById(existingOrderId.get())
                        .orElseThrow(() -> new IllegalStateException("Order not found for idempotency key"));
            }

            CheckoutIntent intent = checkoutIntentService.createIntent(userId, addressId, slotId, slotDate, expectedTotal);

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
                    lineItems
            );

            Order saved = orderRepository.save(newOrder);
            idempotencyKeyRepository.save(idempotencyKey, saved.id());

            return saved;
        });
        
        PaymentReference ref;
        try {
            ref = paymentGateway.initiate(savedOrder.id(), savedOrder.totalAmount());
        } catch (Exception e) {
            throw new PaymentGatewayException(savedOrder.id(), e.getMessage());
        }
        
        return new OrderResponse(savedOrder.id(), savedOrder.status().name(), savedOrder.totalAmount(), ref.paymentUrl());
    }
}
