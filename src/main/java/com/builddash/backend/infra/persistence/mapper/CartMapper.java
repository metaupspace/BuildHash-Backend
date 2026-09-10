package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.CartLineItem;
import com.builddash.backend.infra.persistence.entity.CartLineItemEntity;

/**
 * Line-item mapping only. Cart-level entity→domain mapping lives in CartRepositoryAdapter,
 * which is the only place carts cross that boundary (and the only one that maps companyId —
 * a mapper variant that dropped it was removed after silently breaking the B2B contract).
 */
public final class CartMapper {

    private CartMapper() {}

    public static CartLineItem toDomain(CartLineItemEntity entity) {
        if (entity == null) return null;
        return new CartLineItem(
                entity.getId(),
                entity.getCart() != null ? entity.getCart().getId() : null,
                entity.getProductId(),
                entity.getQuantity(),
                entity.getAppliedItemCoupon(),
                entity.getUnitPriceOverride()
        );
    }
}
