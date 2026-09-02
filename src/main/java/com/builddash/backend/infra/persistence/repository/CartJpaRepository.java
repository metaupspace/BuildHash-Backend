package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartJpaRepository extends JpaRepository<CartEntity, UUID> {

    /** H2.1 CAS: the concurrency guard for B2B draft checkout. Returns 1 iff this call
     *  performed the transition (row exists, was not already consumed); 0 otherwise. */
    @Modifying
    @Query("UPDATE CartEntity c SET c.consumedAt = CURRENT_TIMESTAMP WHERE c.id = :cartId AND c.consumedAt IS NULL")
    int claimForCheckout(@Param("cartId") UUID cartId);

    @Query("SELECT c FROM CartEntity c LEFT JOIN FETCH c.items WHERE c.userId = :userId AND c.type = com.builddash.backend.domain.enums.CartType.PRIMARY AND ((:projectId IS NULL AND c.projectId IS NULL) OR (c.projectId = :projectId))")
    Optional<CartEntity> findByUserIdAndProjectId(@Param("userId") UUID userId, @Param("projectId") UUID projectId);

    @Query("SELECT c FROM CartEntity c LEFT JOIN FETCH c.items WHERE c.id = :id")
    Optional<CartEntity> findByIdWithItems(@Param("id") UUID id);

    @Query("SELECT c FROM CartEntity c LEFT JOIN FETCH c.items WHERE c.type = com.builddash.backend.domain.enums.CartType.PRIMARY AND c.updatedAt < :cutoff AND SIZE(c.items) > 0")
    List<CartEntity> findStalePrimaryCarts(@Param("cutoff") Instant cutoff);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM CartEntity c LEFT JOIN FETCH c.items WHERE c.userId = :userId")
    java.util.List<CartEntity> findAllByUserId(UUID userId);
}
