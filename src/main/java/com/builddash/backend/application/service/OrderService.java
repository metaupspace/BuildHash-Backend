package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.Order;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OrderService {
    /**
     * cartId: null = primary (B2C) cart; a B2B_DRAFT cart id selects the B2B checkout
     * branch. siteId: optional B2B order site (validated against the cart's company);
     * ignored for B2C.
     */
    OrderResult create(UUID userId, UUID addressId, UUID slotId, LocalDate slotDate, BigDecimal expectedTotal,
                       UUID cartId, UUID siteId, String idempotencyKey);
    OrderResult retryPayment(UUID userId, UUID orderId);

    /**
     * 9-D payment resume: retryPayment minus the ownership check — called by
     * ApprovalServiceImpl after the approval transaction committed. Same guards
     * (PAYMENT_PENDING, no PENDING payment), same gateway-outside-tx pattern.
     */
    OrderResult initiatePaymentForApprovedOrder(UUID orderId);
    Order getOrder(UUID userId, UUID orderId);
    List<Order> listOrders(UUID userId);
    List<Order> listOrders(UUID userId, int page, int size);
    ReorderResult reorder(UUID userId, UUID orderId);
}
