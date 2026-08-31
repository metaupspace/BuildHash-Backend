package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.CartType;

import java.util.List;
import java.util.UUID;

/**
 * companyId is the B2B tenant scope (OQ-6: explicit column, project_id keeps its own
 * semantics). Null for every B2C cart; B2B carts (B2B_DRAFT and later B2B checkouts)
 * carry it so pricing resolves the company contract tier and orders can be tagged.
 */
public record Cart(
        UUID id,
        UUID userId,
        UUID projectId,
        CartType type,
        String appliedCartCoupon,
        List<CartLineItem> items,
        UUID companyId
) {

    public Cart {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** Compatibility constructor preserving the pre-9A call shape (companyId = null). */
    public Cart(UUID id, UUID userId, UUID projectId, CartType type, String appliedCartCoupon, List<CartLineItem> items) {
        this(id, userId, projectId, type, appliedCartCoupon, items, null);
    }
}
