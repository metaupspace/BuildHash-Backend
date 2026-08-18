package com.builddash.backend.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface WishlistEntryJpaRepository extends JpaRepository<WishlistEntryEntity, UUID> {

    List<WishlistEntryEntity> findByUserId(UUID userId);

    Optional<WishlistEntryEntity> findByUserIdAndProductId(UUID userId, UUID productId);

    void deleteByUserIdAndProductId(UUID userId, UUID productId);
}
