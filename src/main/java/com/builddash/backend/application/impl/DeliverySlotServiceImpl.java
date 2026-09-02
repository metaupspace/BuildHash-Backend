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

        // 2. Fetch and lock the target (and, if swapping, the prior) counter row(s) by
        //    (slot_id, slot_date) — H2.5: in one canonical order so a concurrent swap
        //    moving the opposite direction can never deadlock against this one.
        DeliverySlotCounter newCounter;
        DeliverySlotCounter oldCounter;
        if (activeLockOpt.isPresent()) {
            DeliverySlotLock oldLock = activeLockOpt.get();
            DeliverySlotCounter[] locked = lockCountersCanonical(slotId, date, oldLock.slotId(), oldLock.slotDate());
            newCounter = locked[0];
            oldCounter = locked[1];
        } else {
            newCounter = deliverySlotCounterRepository.findBySlotIdAndSlotDateForUpdate(slotId, date).orElse(null);
            oldCounter = null;
        }

        if (newCounter == null) {
            throw new SlotUnavailableException("SLOT_NOT_AVAILABLE", "Delivery slot is not available for requested date");
        }

        // 3. Check capacity
        if (!newCounter.hasCapacity()) {
            throw new SlotUnavailableException("SLOT_CAPACITY_EXCEEDED", "Delivery slot capacity reached");
        }

        // 4. Release prior active lock if user held one on a different slot/date.
        //    H2.4: CAS — decrement the old counter only if THIS call won the
        //    ACTIVE -> RELEASED transition (a concurrent release/expiry of the same
        //    lock already returned the capacity; decrementing again would leak it).
        if (activeLockOpt.isPresent()) {
            DeliverySlotLock oldLock = activeLockOpt.get();
            if (deliverySlotLockRepository.tryTransitionStatus(
                    oldLock.id(), DeliverySlotLockStatus.ACTIVE, DeliverySlotLockStatus.RELEASED) == 1) {
                if (oldCounter != null) {
                    deliverySlotCounterRepository.save(oldCounter.decrement());
                }
                log.debug("Released prior lock {} for user {}", oldLock.id(), userId);
            }
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
    public DeliverySlotLock acquireLock(UUID userId, UUID slotId, LocalDate date, Duration ttl) {
        DeliverySlotCounter counter = deliverySlotCounterRepository.findBySlotIdAndSlotDateForUpdate(slotId, date)
                .orElseThrow(() -> new SlotUnavailableException("SLOT_NOT_AVAILABLE", "Delivery slot is not available for requested date"));
        if (!counter.hasCapacity()) {
            throw new SlotUnavailableException("SLOT_CAPACITY_EXCEEDED", "Delivery slot capacity reached");
        }
        deliverySlotCounterRepository.save(counter.increment());
        DeliverySlotLock newLock = new DeliverySlotLock(
                UUID.randomUUID(), userId, slotId, date, Instant.now().plus(ttl), DeliverySlotLockStatus.ACTIVE);
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

        // H2.4 CAS: only the caller that actually flips ACTIVE -> RELEASED decrements the
        // counter — a concurrent release/expiry/consume of the same lock is a no-op here.
        // The counter row is locked BEFORE the lock row so every path in this service
        // takes rows in one global order (counters, then lock rows) — the counterpart of
        // H2.5's canonical counter ordering, without which release-vs-swap could cycle.
        Optional<DeliverySlotCounter> counter = deliverySlotCounterRepository
                .findBySlotIdAndSlotDateForUpdate(lock.slotId(), lock.slotDate());
        int rows = deliverySlotLockRepository.tryTransitionStatus(
                lockId, DeliverySlotLockStatus.ACTIVE, DeliverySlotLockStatus.RELEASED);
        if (rows == 1) {
            counter.ifPresent(c -> deliverySlotCounterRepository.save(c.decrement()));
        }
    }

    @Override
    @Transactional
    public boolean consumeLock(UUID lockId, UUID userId) {
        DeliverySlotLock lock = deliverySlotLockRepository.findById(lockId)
                .orElseThrow(() -> new NotFoundException("LOCK_NOT_FOUND", "Delivery slot lock not found"));

        if (!lock.userId().equals(userId)) {
            throw new UnauthorizedException("ACCESS_DENIED", "Cannot consume lock belonging to another user");
        }

        // No decrement: the confirmed order holds this capacity for delivery. H2.7: the
        // CAS result is the real success/failure signal — false means the lock was not
        // ACTIVE (already released/expired/consumed), which the caller must not treat
        // as a quiet success.
        return deliverySlotLockRepository.tryTransitionStatus(
                lockId, DeliverySlotLockStatus.ACTIVE, DeliverySlotLockStatus.CONSUMED) == 1;
    }

    @Override
    @Transactional
    public DeliverySlotLock swapConsumedLock(UUID userId, UUID oldLockId, UUID oldSlotId, LocalDate oldSlotDate, UUID newSlotId, LocalDate newSlotDate) {
        // H2.5: lock both counters in one canonical order — same helper acquireOrSwapLock
        // uses — so a concurrent swap moving the opposite direction can never deadlock.
        DeliverySlotCounter[] locked = lockCountersCanonical(newSlotId, newSlotDate, oldSlotId, oldSlotDate);
        DeliverySlotCounter newCounter = locked[0];
        DeliverySlotCounter oldCounter = locked[1];

        if (newCounter == null) {
            throw new SlotUnavailableException("SLOT_NOT_AVAILABLE", "Delivery slot is not available for requested date");
        }
        if (!newCounter.hasCapacity()) {
            throw new SlotUnavailableException("SLOT_CAPACITY_EXCEEDED", "Delivery slot capacity reached");
        }

        // 2. Decrement old slot counter & release old lock. H2.4: the decrement rides on
        //    winning the CONSUMED -> RELEASED CAS — one lock can return its capacity once.
        if (deliverySlotLockRepository.tryTransitionStatus(
                oldLockId, DeliverySlotLockStatus.CONSUMED, DeliverySlotLockStatus.RELEASED) == 1
                && oldCounter != null) {
            deliverySlotCounterRepository.save(oldCounter.decrement());
        }

        // 3. Increment new counter & create new lock (status CONSUMED)
        deliverySlotCounterRepository.save(newCounter.increment());

        DeliverySlotLock newLock = new DeliverySlotLock(
                UUID.randomUUID(),
                userId,
                newSlotId,
                newSlotDate,
                Instant.now(),
                DeliverySlotLockStatus.CONSUMED
        );
        return deliverySlotLockRepository.save(newLock);
    }

    @Override
    @Transactional
    public void releaseConsumedLock(UUID lockId, UUID slotId, LocalDate slotDate) {
        // H2.4: same CAS discipline as releaseLock — capacity returns exactly once, only
        // if this call wins the CONSUMED -> RELEASED transition. Counter first, matching
        // the global row order.
        DeliverySlotCounter oldCounter = deliverySlotCounterRepository.findBySlotIdAndSlotDateForUpdate(slotId, slotDate)
                .orElse(null);
        if (deliverySlotLockRepository.tryTransitionStatus(
                lockId, DeliverySlotLockStatus.CONSUMED, DeliverySlotLockStatus.RELEASED) == 1
                && oldCounter != null) {
            deliverySlotCounterRepository.save(oldCounter.decrement());
        }
    }

    /**
     * H2.5: locks both (slot_id, slot_date) counter rows in ONE canonical order —
     * slotId compared first, slotDate as tie-break — so two concurrent swaps in opposite
     * directions can never each hold one row while wanting the other's. Returns the
     * counters positionally, [pair A, pair B], whichever order they were fetched in.
     */
    private DeliverySlotCounter[] lockCountersCanonical(UUID slotIdA, LocalDate dateA, UUID slotIdB, LocalDate dateB) {
        int cmp = slotIdA.compareTo(slotIdB);
        if (cmp == 0 && dateA.compareTo(dateB) == 0) {
            // Same slot and date: one row, fetched once — returning it twice keeps the
            // caller's decrement/increment pair operating on the same instance.
            DeliverySlotCounter same = deliverySlotCounterRepository.findBySlotIdAndSlotDateForUpdate(slotIdA, dateA)
                    .orElse(null);
            return new DeliverySlotCounter[]{same, same};
        }
        boolean aFirst = cmp < 0 || (cmp == 0 && dateA.compareTo(dateB) < 0);
        DeliverySlotCounter first = deliverySlotCounterRepository.findBySlotIdAndSlotDateForUpdate(
                aFirst ? slotIdA : slotIdB, aFirst ? dateA : dateB).orElse(null);
        DeliverySlotCounter second = deliverySlotCounterRepository.findBySlotIdAndSlotDateForUpdate(
                aFirst ? slotIdB : slotIdA, aFirst ? dateB : dateA).orElse(null);
        return aFirst ? new DeliverySlotCounter[]{first, second} : new DeliverySlotCounter[]{second, first};
    }
}
