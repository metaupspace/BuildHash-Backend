package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CartJpaRepository extends JpaRepository<CartEntity, UUID> {

    @Query("SELECT c FROM CartEntity c LEFT JOIN FETCH c.items WHERE c.userId = :userId AND c.type = com.builddash.backend.domain.enums.CartType.PRIMARY AND ((:projectId IS NULL AND c.projectId IS NULL) OR (c.projectId = :projectId))")
    Optional<CartEntity> findByUserIdAndProjectId(@Param("userId") UUID userId, @Param("projectId") UUID projectId);

    @Query("SELECT c FROM CartEntity c LEFT JOIN FETCH c.items WHERE c.id = :id")
    Optional<CartEntity> findByIdWithItems(@Param("id") UUID id);
}
