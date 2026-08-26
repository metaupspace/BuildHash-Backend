package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.ReturnEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReturnJpaRepository extends JpaRepository<ReturnEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = {"lineItems"})
    Optional<ReturnEntity> findById(UUID id);

    @EntityGraph(attributePaths = {"lineItems"})
    Optional<ReturnEntity> findByOrderId(UUID orderId);

    @EntityGraph(attributePaths = {"lineItems"})
    List<ReturnEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
