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
    public CartLineItem save(CartLineItem item) {
        CartLineItemEntity entity = jpaRepository.findByCartIdAndProductId(item.cartId(), item.productId())
                .orElseGet(() -> {
                    CartLineItemEntity e = new CartLineItemEntity();
                    e.setId(item.id() != null ? item.id() : UUID.randomUUID());
                    e.setCart(cartJpaRepository.getReferenceById(item.cartId()));
                    e.setProductId(item.productId());
                    return e;
                });
        entity.setQuantity(item.quantity());
        entity.setAppliedItemCoupon(item.appliedItemCoupon());
        CartLineItemEntity saved = jpaRepository.save(entity);
        return CartMapper.toDomain(saved);
    }

    @Override
    public void deleteByCartIdAndProductId(UUID cartId, UUID productId) {
        jpaRepository.deleteByCartIdAndProductId(cartId, productId);
    }

    @Override
    public void deleteByCartId(UUID cartId) {
        jpaRepository.deleteByCartId(cartId);
    }
}
