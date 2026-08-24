package com.builddash.backend.application.service;

import com.builddash.backend.api.dto.response.OrderResponse;
import com.builddash.backend.api.dto.response.ReorderResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OrderService {
    OrderResponse create(UUID userId, UUID addressId, UUID slotId, LocalDate slotDate, BigDecimal expectedTotal, String idempotencyKey);
    OrderResponse retryPayment(UUID userId, UUID orderId);
    OrderResponse getOrder(UUID userId, UUID orderId);
    List<OrderResponse> listOrders(UUID userId);
    com.builddash.backend.api.dto.response.PricedCartResponse reorder(UUID userId, UUID orderId);
}
