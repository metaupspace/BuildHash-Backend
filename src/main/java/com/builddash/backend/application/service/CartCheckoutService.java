package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.CartLineItem;
import com.builddash.backend.domain.model.PricedCart;

import java.util.List;
import java.util.UUID;

/**
 * Cart provisioning for checkout and conversion flows — the CartService subset used by
 * order creation, reorder, and RFQ/PO draft conversion, not by the cart-owning user's
 * CRUD surface (interface segregation: those clients never see coupon/cart mutation).
 */
public interface CartCheckoutService {

    PricedCart createReorderCart(UUID userId, List<CartLineItem> items);

    /**
     * 9-B/9-C conversion target: a B2B_DRAFT cart owned by the converting user,
     * scoped to the company, projectId = sourceId (the RFQ or PO id). Enters the
     * existing checkout flow later — no order, payment or approval here.
     */
    PricedCart createB2bDraftCart(UUID companyId, UUID userId, UUID sourceId, List<CartLineItem> items);

    /**
     * H2.1: atomic one-time consumption claim for a B2B draft cart's checkout. True iff
     * this call claimed it (not already consumed) — the concurrency guard that makes two
     * concurrent checkouts against the same draft produce exactly one winner.
     */
    boolean claimForCheckout(UUID cartId);
}
