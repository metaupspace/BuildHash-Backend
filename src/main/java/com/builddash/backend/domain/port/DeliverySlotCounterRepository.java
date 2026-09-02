package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.DeliverySlotCounter;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliverySlotCounterRepository {
    Optional<DeliverySlotCounter> findBySlotIdAndSlotDate(UUID slotId, LocalDate slotDate);
    Optional<DeliverySlotCounter> findBySlotIdAndSlotDateForUpdate(UUID slotId, LocalDate slotDate);
    List<DeliverySlotCounter> findBySlotDate(LocalDate slotDate);
    DeliverySlotCounter save(DeliverySlotCounter counter);
    boolean existsBySlotIdAndSlotDate(UUID slotId, LocalDate slotDate);
    void insertIfNotExists(UUID id, UUID slotId, LocalDate slotDate, int capacity);
}
