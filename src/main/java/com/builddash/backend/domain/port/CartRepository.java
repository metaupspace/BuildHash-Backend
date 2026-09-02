package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Cart;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository {
    Optional<Cart> findByUserIdAndProjectId(UUID userId, UUID projectId);
    Optional<Cart> findById(UUID id);
    Cart save(Cart cart);
    void delete(UUID id);

    /** The cart-abandonment job's only sanctioned Cart read (PLAN_PHASE7 5(f)): PRIMARY carts not touched since the cutoff, with items. */
    List<Cart> findStalePrimaryCarts(Instant cutoff);

    /** DPDP export: every cart (any type) owned by the user, with items. */
    List<Cart> findAllByUserId(UUID userId);

    /** H2.1: atomic one-time consumption claim. True iff this call performed the
     *  transition (cart existed, was not already consumed) — the concurrency guard for
     *  B2B draft checkout, not a courtesy status read. */
    boolean claimForCheckout(UUID cartId);
}
