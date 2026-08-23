package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.PricedCartLineItemResponse;
import com.builddash.backend.api.dto.response.PricedCartResponse;
import com.builddash.backend.domain.model.PricedCart;
import com.builddash.backend.domain.model.PricedCartLineItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CartDtoMapper {

    public PricedCartResponse toResponse(PricedCart cart) {
        if (cart == null) return null;
        List<PricedCartLineItemResponse> items = cart.items() != null
                ? cart.items().stream().map(this::toLineItemResponse).toList()
                : List.of();

        return new PricedCartResponse(
                cart.id(),
                cart.userId(),
                cart.projectId(),
                items,
                cart.subtotal(),
                cart.itemDiscountsTotal(),
                cart.cartDiscountTotal(),
                cart.totalGst(),
                cart.finalTotal(),
                cart.appliedCartCoupon(),
                cart.couponDroppedReason()
        );
    }

    public PricedCartLineItemResponse toLineItemResponse(PricedCartLineItem item) {
        if (item == null) return null;
        return new PricedCartLineItemResponse(
                item.productId(),
                item.quantity(),
                item.hsnCode(),
                item.unitBasePrice(),
                item.unitFinalPrice(),
                item.lineSubtotal(),
                item.lineDiscount(),
                item.lineGst(),
                item.lineFinalTotal(),
                item.appliedItemCoupon()
        );
    }
}
