package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.RefundEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundJpaRepository extends JpaRepository<RefundEntity, UUID> {
    Optional<RefundEntity> findByReturnId(UUID returnId);
    Optional<RefundEntity> findFirstByReturnIdOrderByCreatedAtDescIdDesc(UUID returnId);
    Optional<RefundEntity> findByGatewayRefundId(String gatewayRefundId);
    List<RefundEntity> findAllByReturnIdOrderByCreatedAtDesc(UUID returnId);

    /** OrderJpaRepository/ReturnJpaRepository.findByIdForUpdate shape: SELECT ... FOR UPDATE. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefundEntity r WHERE r.id = :id")
    Optional<RefundEntity> findByIdForUpdate(@Param("id") UUID id);
}
