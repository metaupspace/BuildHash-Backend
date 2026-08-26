package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.RefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundJpaRepository extends JpaRepository<RefundEntity, UUID> {
    Optional<RefundEntity> findByReturnId(UUID returnId);
    Optional<RefundEntity> findByGatewayRefundId(String gatewayRefundId);
    List<RefundEntity> findAllByReturnIdOrderByCreatedAtDesc(UUID returnId);
}
