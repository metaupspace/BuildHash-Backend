package com.builddash.backend.infra.persistence.order;

import com.builddash.backend.domain.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT o.id FROM OrderEntity o WHERE o.status = :status AND o.deliverySlotLockId IN (SELECT l.id FROM com.builddash.backend.infra.persistence.entity.DeliverySlotLockEntity l WHERE l.expiresAt < :cutoff)")
    List<UUID> findStalePaymentPendingOrderIds(@Param("status") OrderStatus status, @Param("cutoff") Instant cutoff);

    List<OrderEntity> findAllByUserIdOrderByPlacedAtDesc(UUID userId);
}
