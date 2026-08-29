package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.Cart;
import com.builddash.backend.domain.port.CartRepository;
import com.builddash.backend.infra.persistence.entity.CartEntity;
import com.builddash.backend.infra.persistence.entity.CartLineItemEntity;
import com.builddash.backend.infra.persistence.mapper.CartMapper;
import com.builddash.backend.infra.persistence.repository.CartJpaRepository;
import com.builddash.backend.infra.persistence.repository.CartLineItemJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class CartRepositoryAdapter implements CartRepository {

    private final CartJpaRepository jpaRepository;
    private final CartLineItemJpaRepository cartLineItemJpaRepository;

    @Override
    public Optional<Cart> findByUserIdAndProjectId(UUID userId, UUID projectId) {
        // Both JPA queries JOIN-FETCH items — map from the entity, don't re-query
        return jpaRepository.findByUserIdAndProjectId(userId, projectId)
                .map(this::toDomain);
    }

    @Override
    public Optional<Cart> findById(UUID id) {
        return jpaRepository.findByIdWithItems(id)
                .map(this::toDomain);
    }

    @Override
    public List<Cart> findStalePrimaryCarts(Instant cutoff) {
        return jpaRepository.findStalePrimaryCarts(cutoff).stream()
                .map(this::toDomain)
                .toList();
    }

    private Cart toDomain(CartEntity entity) {
        return new Cart(entity.getId(), entity.getUserId(), entity.getProjectId(), entity.getType(), entity.getAppliedCartCoupon(),
                entity.getItems().stream().map(CartMapper::toDomain).toList());
    }

    @Override
    public Cart save(Cart cart) {
        CartEntity entity = jpaRepository.findById(cart.id())
                .orElseGet(() -> {
                    CartEntity e = new CartEntity();
                    e.setId(cart.id());
                    return e;
                });
        entity.setUserId(cart.userId());
        entity.setProjectId(cart.projectId());
        if (cart.type() != null) {
            entity.setType(cart.type());
        }
        entity.setAppliedCartCoupon(cart.appliedCartCoupon());
        CartEntity saved = jpaRepository.save(entity);
        List<CartLineItemEntity> items = cartLineItemJpaRepository.findByCartId(saved.getId());
        return new Cart(saved.getId(), saved.getUserId(), saved.getProjectId(), saved.getType(), saved.getAppliedCartCoupon(),
                items.stream().map(CartMapper::toDomain).toList());
    }

    @Override
    public void delete(UUID id) {
        jpaRepository.deleteById(id);
    }
}