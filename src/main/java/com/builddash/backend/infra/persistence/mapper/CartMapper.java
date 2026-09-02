package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.Cart;
import com.builddash.backend.domain.model.CartLineItem;
import com.builddash.backend.infra.persistence.entity.CartEntity;
import com.builddash.backend.infra.persistence.entity.CartLineItemEntity;

import java.util.ArrayList;
import java.util.List;

public final class CartMapper {

    private CartMapper() {}

    public static Cart toDomain(CartEntity entity) {
        if (entity == null) return null;
        List<CartLineItem> items = entity.getItems() != null
                ? entity.getItems().stream().map(CartMapper::toDomain).toList()
                : List.of();
        return new Cart(
                entity.getId(),
                entity.getUserId(),
                entity.getProjectId(),
                entity.getType(),
                entity.getAppliedCartCoupon(),
                items
        );
    }

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

    public static CartEntity toEntity(Cart domain) {
        if (domain == null) return null;
        CartEntity entity = new CartEntity();
        entity.setId(domain.id());
        entity.setUserId(domain.userId());
        entity.setProjectId(domain.projectId());
        if (domain.type() != null) {
            entity.setType(domain.type());
        }
        entity.setAppliedCartCoupon(domain.appliedCartCoupon());
        if (domain.items() != null) {
            List<CartLineItemEntity> items = new ArrayList<>();
            for (CartLineItem item : domain.items()) {
                CartLineItemEntity itemEntity = toEntity(item, entity);
                items.add(itemEntity);
            }
            entity.setItems(items);
        }
        return entity;
    }

    public static CartLineItemEntity toEntity(CartLineItem domain, CartEntity cartEntity) {
        if (domain == null) return null;
        CartLineItemEntity entity = new CartLineItemEntity();
        entity.setId(domain.id());
        entity.setCart(cartEntity);
        entity.setProductId(domain.productId());
        entity.setQuantity(domain.quantity());
        entity.setAppliedItemCoupon(domain.appliedItemCoupon());
        entity.setUnitPriceOverride(domain.unitPriceOverride());
        return entity;
    }
}
