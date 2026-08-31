package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.ReturnEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReturnJpaRepository extends JpaRepository<ReturnEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = {"lineItems"})
    Optional<ReturnEntity> findById(UUID id);

    /** OrderJpaRepository.findByIdForUpdate shape (8.1-C): SELECT ... FOR UPDATE with items. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReturnEntity r LEFT JOIN FETCH r.lineItems WHERE r.id = :id")
    Optional<ReturnEntity> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"lineItems"})
    Optional<ReturnEntity> findByOrderId(UUID orderId);

    @EntityGraph(attributePaths = {"lineItems"})
    List<ReturnEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
