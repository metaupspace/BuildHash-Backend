package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.PricedCart;

import java.util.UUID;

public interface CartService {

    PricedCart getCart(UUID userId, UUID projectId);

    PricedCart upsertItem(UUID userId, UUID projectId, UUID productId, int quantity, String itemCoupon);

    PricedCart removeItem(UUID userId, UUID projectId, UUID productId);

    PricedCart applyCartCoupon(UUID userId, UUID projectId, String couponCode);

    PricedCart removeCartCoupon(UUID userId, UUID projectId);

    void clearCart(UUID userId, UUID projectId);

    PricedCart getCartById(UUID userId, UUID cartId);

    PricedCart createReorderCart(UUID userId, java.util.List<com.builddash.backend.domain.model.CartLineItem> items);

    /**
     * 9-B/9-C conversion target: a B2B_DRAFT cart owned by the converting user,
     * scoped to the company, projectId = sourceId (the RFQ or PO id). Enters the
     * existing checkout flow later — no order, payment or approval here.
     */
    PricedCart createB2bDraftCart(UUID companyId, UUID userId, UUID sourceId,
                                  java.util.List<com.builddash.backend.domain.model.CartLineItem> items);
    void mergeGuestCart(UUID guestUserId, UUID realUserId);
}
