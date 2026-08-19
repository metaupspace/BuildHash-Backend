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
}
