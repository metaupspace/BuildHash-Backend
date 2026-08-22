package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.DeliverySlotCounterEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliverySlotCounterJpaRepository extends JpaRepository<DeliverySlotCounterEntity, UUID> {

    Optional<DeliverySlotCounterEntity> findBySlotIdAndSlotDate(UUID slotId, LocalDate slotDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DeliverySlotCounterEntity c WHERE c.slotId = :slotId AND c.slotDate = :slotDate")
    Optional<DeliverySlotCounterEntity> findBySlotIdAndSlotDateForUpdate(@Param("slotId") UUID slotId, @Param("slotDate") LocalDate slotDate);

    List<DeliverySlotCounterEntity> findBySlotDate(LocalDate slotDate);

    boolean existsBySlotIdAndSlotDate(UUID slotId, LocalDate slotDate);
}
