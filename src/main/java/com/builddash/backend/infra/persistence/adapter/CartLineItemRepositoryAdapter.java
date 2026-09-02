package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.CartLineItem;
import com.builddash.backend.domain.port.CartLineItemRepository;
import com.builddash.backend.infra.persistence.entity.CartLineItemEntity;
import com.builddash.backend.infra.persistence.mapper.CartMapper;
import com.builddash.backend.infra.persistence.repository.CartJpaRepository;
import com.builddash.backend.infra.persistence.repository.CartLineItemJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class CartLineItemRepositoryAdapter implements CartLineItemRepository {

    private final CartLineItemJpaRepository jpaRepository;
    private final CartJpaRepository cartJpaRepository;

    @Override
    public List<CartLineItem> findByCartId(UUID cartId) {
        return jpaRepository.findByCartId(cartId).stream()
                .map(CartMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<CartLineItem> findByCartIdAndProductId(UUID cartId, UUID productId) {
        return jpaRepository.findByCartIdAndProductId(cartId, productId)
                .map(CartMapper::toDomain);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public CartLineItem save(CartLineItem item) {
        UUID id = item.id() != null ? item.id() : UUID.randomUUID();
        jpaRepository.upsert(id, item.cartId(), item.productId(), item.quantity(), item.appliedItemCoupon(), item.unitPriceOverride());
        CartLineItemEntity saved = jpaRepository.findByCartIdAndProductId(item.cartId(), item.productId())
                .orElseThrow();

        // Keep the bidirectional collection consistent within the session — a cart
        // read later in the same transaction maps from CartEntity.items and must
        // see this row without a redundant re-query.
        cartJpaRepository.findById(item.cartId()).ifPresent(cart -> {
            cart.getItems().removeIf(existing -> existing.getId().equals(saved.getId()));
            cart.getItems().add(saved);
        });

        return CartMapper.toDomain(saved);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void deleteByCartIdAndProductId(UUID cartId, UUID productId) {
        jpaRepository.deleteByCartIdAndProductId(cartId, productId);
        cartJpaRepository.findById(cartId)
                .ifPresent(cart -> cart.getItems().removeIf(item -> item.getProductId().equals(productId)));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void deleteByCartId(UUID cartId) {
        jpaRepository.deleteByCartId(cartId);
    }
}
