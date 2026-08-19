package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.domain.enums.DeliverySlotLockStatus;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.SlotUnavailableException;
import com.builddash.backend.domain.exception.UnauthorizedException;
import com.builddash.backend.domain.model.DeliverySlotCounter;
import com.builddash.backend.domain.model.DeliverySlotLock;
import com.builddash.backend.domain.model.DeliverySlotOption;
import com.builddash.backend.domain.model.SlotConfiguration;
import com.builddash.backend.domain.port.DeliverySlotCounterRepository;
import com.builddash.backend.domain.port.DeliverySlotLockRepository;
import com.builddash.backend.domain.port.SlotConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliverySlotServiceImpl implements DeliverySlotService {

    private final SlotConfigurationRepository slotConfigurationRepository;
    private final DeliverySlotCounterRepository deliverySlotCounterRepository;
    private final DeliverySlotLockRepository deliverySlotLockRepository;

    @Override
    @Transactional(readOnly = true)
    public List<DeliverySlotOption> getAvailableSlots(LocalDate date) {
        List<SlotConfiguration> activeConfigs = slotConfigurationRepository.findAllActive();
        List<DeliverySlotCounter> counters = deliverySlotCounterRepository.findBySlotDate(date);
        Map<UUID, DeliverySlotCounter> countersBySlotId = counters.stream()
                .collect(Collectors.toMap(DeliverySlotCounter::slotId, Function.identity()));

        List<DeliverySlotOption> options = new ArrayList<>();
        for (SlotConfiguration config : activeConfigs) {
            DeliverySlotCounter counter = countersBySlotId.get(config.id());
            int available = (counter != null) ? Math.max(0, counter.capacity() - counter.currentCount()) : 0;
            int capacity = (counter != null) ? counter.capacity() : config.capacity();
            options.add(new DeliverySlotOption(
                    config.id(),
                    config.startTime(),
                    config.endTime(),
                    date,
                    capacity,
                    available
            ));
        }
        return options;
    }

    /**
     * Atomically releases previous lock (if any) and acquires new slot lock.
     * If new slot acquisition fails, entire transaction rolls back preserving old lock.
     */
    @Override
    @Transactional
    public DeliverySlotLock acquireOrSwapLock(UUID userId, UUID slotId, LocalDate date, Duration ttl) {
        Instant now = Instant.now();

        // 1. Check if user already holds active lock on this exact slot and date
        Optional<DeliverySlotLock> activeLockOpt = deliverySlotLockRepository.findActiveByUserId(userId, now);
        if (activeLockOpt.isPresent()) {
            DeliverySlotLock existingLock = activeLockOpt.get();
            if (existingLock.slotId().equals(slotId) && existingLock.slotDate().equals(date)) {
                // Extend existing lock
                DeliverySlotLock extended = new DeliverySlotLock(
                        existingLock.id(),
                        userId,
                        slotId,
                        date,
                        now.plus(ttl),
                        DeliverySlotLockStatus.ACTIVE
                );
                return deliverySlotLockRepository.save(extended);
            }
        }

        // 2. Fetch and lock target counter row specifically by (slot_id, slot_date)
        DeliverySlotCounter newCounter = deliverySlotCounterRepository.findBySlotIdAndSlotDateForUpdate(slotId, date)
                .orElseThrow(() -> new SlotUnavailableException("SLOT_NOT_AVAILABLE", "Delivery slot is not available for requested date"));

        // 3. Check capacity
        if (!newCounter.hasCapacity()) {
            throw new SlotUnavailableException("SLOT_CAPACITY_EXCEEDED", "Delivery slot capacity reached");
        }

        // 4. Release prior active lock if user held one on a different slot/date
        if (activeLockOpt.isPresent()) {
            DeliverySlotLock oldLock = activeLockOpt.get();
            DeliverySlotCounter oldCounter = deliverySlotCounterRepository.findBySlotIdAndSlotDateForUpdate(oldLock.slotId(), oldLock.slotDate())
                    .orElse(null);
            if (oldCounter != null) {
                deliverySlotCounterRepository.save(oldCounter.decrement());
            }
            deliverySlotLockRepository.updateStatus(oldLock.id(), DeliverySlotLockStatus.RELEASED);
            log.debug("Released prior lock {} for user {}", oldLock.id(), userId);
        }

        // 5. Increment new slot counter
        deliverySlotCounterRepository.save(newCounter.increment());

        // 6. Create and return new lock
        DeliverySlotLock newLock = new DeliverySlotLock(
                UUID.randomUUID(),
                userId,
                slotId,
                date,
                now.plus(ttl),
                DeliverySlotLockStatus.ACTIVE
        );
        return deliverySlotLockRepository.save(newLock);
    }

    @Override
    @Transactional
    public void releaseLock(UUID lockId, UUID userId) {
        DeliverySlotLock lock = deliverySlotLockRepository.findById(lockId)
                .orElseThrow(() -> new NotFoundException("LOCK_NOT_FOUND", "Delivery slot lock not found"));

        if (!lock.userId().equals(userId)) {
            throw new UnauthorizedException("ACCESS_DENIED", "Cannot release lock belonging to another user");
        }

        if (lock.status() == DeliverySlotLockStatus.ACTIVE) {
            DeliverySlotCounter counter = deliverySlotCounterRepository.findBySlotIdAndSlotDateForUpdate(lock.slotId(), lock.slotDate())
                    .orElse(null);
            if (counter != null) {
                deliverySlotCounterRepository.save(counter.decrement());
            }
            deliverySlotLockRepository.updateStatus(lockId, DeliverySlotLockStatus.RELEASED);
        }
    }
}
