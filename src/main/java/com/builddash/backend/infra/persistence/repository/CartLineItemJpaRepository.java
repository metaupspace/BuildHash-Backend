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

    /**
     * H2.9: one atomic upsert on (cart_id, product_id) instead of select-then-insert —
     * two concurrent identical adds now resolve to one row (last write wins on quantity,
     * matching the prior select-then-overwrite semantics) instead of racing into
     * uq_cart_line_item_product and surfacing a raw DataIntegrityViolationException.
     */
    @Modifying
    @Query(value = "INSERT INTO cart_line_items (id, cart_id, product_id, quantity, applied_item_coupon, created_at, updated_at) "
            + "VALUES (:id, :cartId, :productId, :quantity, :appliedItemCoupon, now(), now()) "
            + "ON CONFLICT (cart_id, product_id) DO UPDATE SET "
            + "quantity = EXCLUDED.quantity, applied_item_coupon = EXCLUDED.applied_item_coupon, updated_at = now()",
            nativeQuery = true)
    void upsert(@Param("id") UUID id, @Param("cartId") UUID cartId, @Param("productId") UUID productId,
                @Param("quantity") int quantity, @Param("appliedItemCoupon") String appliedItemCoupon);
}
