package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.CartLineItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartLineItemRepository {
    List<CartLineItem> findByCartId(UUID cartId);
    Optional<CartLineItem> findByCartIdAndProductId(UUID cartId, UUID productId);
    CartLineItem save(CartLineItem item);
    void deleteByCartIdAndProductId(UUID cartId, UUID productId);
    void deleteByCartId(UUID cartId);
}
