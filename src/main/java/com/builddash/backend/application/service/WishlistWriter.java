package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.WishlistEntry;

import java.util.UUID;

public interface WishlistWriter {

    /** Idempotent: returns the existing entry if the product is already wishlisted. */
    WishlistEntry add(UUID userId, UUID productId);

    void remove(UUID userId, UUID productId);
}
