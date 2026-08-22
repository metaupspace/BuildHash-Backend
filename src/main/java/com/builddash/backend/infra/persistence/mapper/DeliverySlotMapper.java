package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.DeliverySlotCounter;
import com.builddash.backend.domain.model.DeliverySlotLock;
import com.builddash.backend.domain.model.SlotConfiguration;
import com.builddash.backend.infra.persistence.entity.DeliverySlotCounterEntity;
import com.builddash.backend.infra.persistence.entity.DeliverySlotLockEntity;
import com.builddash.backend.infra.persistence.entity.SlotConfigurationEntity;

public final class DeliverySlotMapper {

    private DeliverySlotMapper() {}

    public static SlotConfiguration toDomain(SlotConfigurationEntity entity) {
        if (entity == null) return null;
        return new SlotConfiguration(
                entity.getId(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getCapacity(),
                entity.isActive()
        );
    }

    public static SlotConfigurationEntity toEntity(SlotConfiguration domain) {
        if (domain == null) return null;
        SlotConfigurationEntity entity = new SlotConfigurationEntity();
        entity.setId(domain.id());
        entity.setStartTime(domain.startTime());
        entity.setEndTime(domain.endTime());
        entity.setCapacity(domain.capacity());
        entity.setActive(domain.isActive());
        return entity;
    }

    public static DeliverySlotCounter toDomain(DeliverySlotCounterEntity entity) {
        if (entity == null) return null;
        return new DeliverySlotCounter(
                entity.getId(),
                entity.getSlotId(),
                entity.getSlotDate(),
                entity.getCapacity(),
                entity.getCurrentCount()
        );
    }

    public static DeliverySlotCounterEntity toEntity(DeliverySlotCounter domain) {
        if (domain == null) return null;
        DeliverySlotCounterEntity entity = new DeliverySlotCounterEntity();
        entity.setId(domain.id());
        entity.setSlotId(domain.slotId());
        entity.setSlotDate(domain.slotDate());
        entity.setCapacity(domain.capacity());
        entity.setCurrentCount(domain.currentCount());
        return entity;
    }

    public static DeliverySlotLock toDomain(DeliverySlotLockEntity entity) {
        if (entity == null) return null;
        return new DeliverySlotLock(
                entity.getId(),
                entity.getUserId(),
                entity.getSlotId(),
                entity.getSlotDate(),
                entity.getExpiresAt(),
                entity.getStatus()
        );
    }

    public static DeliverySlotLockEntity toEntity(DeliverySlotLock domain) {
        if (domain == null) return null;
        DeliverySlotLockEntity entity = new DeliverySlotLockEntity();
        entity.setId(domain.id());
        entity.setUserId(domain.userId());
        entity.setSlotId(domain.slotId());
        entity.setSlotDate(domain.slotDate());
        entity.setExpiresAt(domain.expiresAt());
        entity.setStatus(domain.status());
        return entity;
    }
}
