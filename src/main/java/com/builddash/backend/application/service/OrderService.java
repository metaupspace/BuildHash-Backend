package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.Order;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OrderService {
    OrderResult create(UUID userId, UUID addressId, UUID slotId, LocalDate slotDate, BigDecimal expectedTotal, String idempotencyKey);
    OrderResult retryPayment(UUID userId, UUID orderId);
    Order getOrder(UUID userId, UUID orderId);
    List<Order> listOrders(UUID userId);
    ReorderResult reorder(UUID userId, UUID orderId);
}
