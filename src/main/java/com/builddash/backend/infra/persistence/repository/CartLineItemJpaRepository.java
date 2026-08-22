package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.CartLineItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartLineItemJpaRepository extends JpaRepository<CartLineItemEntity, UUID> {

    @Query("SELECT i FROM CartLineItemEntity i WHERE i.cart.id = :cartId")
    List<CartLineItemEntity> findByCartId(@Param("cartId") UUID cartId);

    @Query("SELECT i FROM CartLineItemEntity i WHERE i.cart.id = :cartId AND i.productId = :productId")
    Optional<CartLineItemEntity> findByCartIdAndProductId(@Param("cartId") UUID cartId, @Param("productId") UUID productId);

    @Modifying
    @Query("DELETE FROM CartLineItemEntity i WHERE i.cart.id = :cartId AND i.productId = :productId")
    void deleteByCartIdAndProductId(@Param("cartId") UUID cartId, @Param("productId") UUID productId);

    @Modifying
    @Query("DELETE FROM CartLineItemEntity i WHERE i.cart.id = :cartId")
    void deleteByCartId(@Param("cartId") UUID cartId);
}
