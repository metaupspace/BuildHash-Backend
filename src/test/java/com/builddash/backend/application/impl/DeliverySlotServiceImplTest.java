package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.domain.enums.DeliverySlotLockStatus;
import com.builddash.backend.domain.exception.SlotUnavailableException;
import com.builddash.backend.domain.model.DeliverySlotCounter;
import com.builddash.backend.domain.model.DeliverySlotLock;
import com.builddash.backend.domain.model.DeliverySlotOption;
import com.builddash.backend.domain.model.SlotConfiguration;
import com.builddash.backend.domain.port.DeliverySlotCounterRepository;
import com.builddash.backend.domain.port.DeliverySlotLockRepository;
import com.builddash.backend.domain.port.SlotConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliverySlotServiceImplTest {

    private SlotConfigurationRepository slotConfigurationRepository;
    private DeliverySlotCounterRepository deliverySlotCounterRepository;
    private DeliverySlotLockRepository deliverySlotLockRepository;
    private DeliverySlotService deliverySlotService;

    @BeforeEach
    void setUp() {
        slotConfigurationRepository = mock(SlotConfigurationRepository.class);
        deliverySlotCounterRepository = mock(DeliverySlotCounterRepository.class);
        deliverySlotLockRepository = mock(DeliverySlotLockRepository.class);
        deliverySlotService = new DeliverySlotServiceImpl(
                slotConfigurationRepository,
                deliverySlotCounterRepository,
                deliverySlotLockRepository
        );
    }

    @Test
    void getAvailableSlots_computesAvailableCapacityAccurately() {
        UUID slotId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 1);
        SlotConfiguration config = new SlotConfiguration(slotId, LocalTime.of(9, 0), LocalTime.of(12, 0), 50, true);
        DeliverySlotCounter counter = new DeliverySlotCounter(UUID.randomUUID(), slotId, date, 50, 10);

        when(slotConfigurationRepository.findAllActive()).thenReturn(List.of(config));
        when(deliverySlotCounterRepository.findBySlotDate(date)).thenReturn(List.of(counter));

        List<DeliverySlotOption> options = deliverySlotService.getAvailableSlots(date);

        assertThat(options).hasSize(1);
        assertThat(options.get(0).availableCount()).isEqualTo(40);
        assertThat(options.get(0).capacity()).isEqualTo(50);
    }

    @Test
    void acquireOrSwapLock_happyPath_locksAndIncrementsCounter() {
        UUID userId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 1);
        DeliverySlotCounter counter = new DeliverySlotCounter(UUID.randomUUID(), slotId, date, 50, 5);

        when(deliverySlotLockRepository.findActiveByUserId(eq(userId), any(Instant.class))).thenReturn(Optional.empty());
        when(deliverySlotCounterRepository.findBySlotIdAndSlotDateForUpdate(slotId, date)).thenReturn(Optional.of(counter));
        when(deliverySlotLockRepository.save(any(DeliverySlotLock.class))).thenAnswer(inv -> inv.getArgument(0));

        DeliverySlotLock lock = deliverySlotService.acquireOrSwapLock(userId, slotId, date, Duration.ofMinutes(15));

        assertThat(lock.userId()).isEqualTo(userId);
        assertThat(lock.slotId()).isEqualTo(slotId);
        assertThat(lock.status()).isEqualTo(DeliverySlotLockStatus.ACTIVE);

        verify(deliverySlotCounterRepository).save(new DeliverySlotCounter(counter.id(), slotId, date, 50, 6));
    }

    @Test
    void acquireOrSwapLock_whenCapacityExceeded_throwsException() {
        UUID userId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 1);
        DeliverySlotCounter fullCounter = new DeliverySlotCounter(UUID.randomUUID(), slotId, date, 50, 50);

        when(deliverySlotLockRepository.findActiveByUserId(eq(userId), any(Instant.class))).thenReturn(Optional.empty());
        when(deliverySlotCounterRepository.findBySlotIdAndSlotDateForUpdate(slotId, date)).thenReturn(Optional.of(fullCounter));

        assertThatThrownBy(() -> deliverySlotService.acquireOrSwapLock(userId, slotId, date, Duration.ofMinutes(15)))
                .isInstanceOf(SlotUnavailableException.class)
                .hasMessageContaining("capacity reached");

        verify(deliverySlotCounterRepository, never()).save(any());
        verify(deliverySlotLockRepository, never()).save(any());
    }

    @Test
    void acquireOrSwapLock_whenCounterMissing_failsClosed() {
        UUID userId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 1);

        when(deliverySlotLockRepository.findActiveByUserId(eq(userId), any(Instant.class))).thenReturn(Optional.empty());
        when(deliverySlotCounterRepository.findBySlotIdAndSlotDateForUpdate(slotId, date)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deliverySlotService.acquireOrSwapLock(userId, slotId, date, Duration.ofMinutes(15)))
                .isInstanceOf(SlotUnavailableException.class)
                .hasMessageContaining("not available for requested date");
    }

    @Test
    void acquireOrSwapLock_swapsPriorLockAtomically() {
        UUID userId = UUID.randomUUID();
        UUID oldSlotId = UUID.randomUUID();
        UUID newSlotId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 9, 1);

        DeliverySlotLock oldLock = new DeliverySlotLock(UUID.randomUUID(), userId, oldSlotId, date, Instant.now().plusSeconds(300), DeliverySlotLockStatus.ACTIVE);
        DeliverySlotCounter oldCounter = new DeliverySlotCounter(UUID.randomUUID(), oldSlotId, date, 50, 10);
        DeliverySlotCounter newCounter = new DeliverySlotCounter(UUID.randomUUID(), newSlotId, date, 50, 0);

        when(deliverySlotLockRepository.findActiveByUserId(eq(userId), any(Instant.class))).thenReturn(Optional.of(oldLock));
        when(deliverySlotCounterRepository.findBySlotIdAndSlotDateForUpdate(newSlotId, date)).thenReturn(Optional.of(newCounter));
        when(deliverySlotCounterRepository.findBySlotIdAndSlotDateForUpdate(oldSlotId, date)).thenReturn(Optional.of(oldCounter));
        when(deliverySlotLockRepository.save(any(DeliverySlotLock.class))).thenAnswer(inv -> inv.getArgument(0));

        DeliverySlotLock newLock = deliverySlotService.acquireOrSwapLock(userId, newSlotId, date, Duration.ofMinutes(15));

        assertThat(newLock.slotId()).isEqualTo(newSlotId);

        // Verifies old counter decremented
        verify(deliverySlotCounterRepository).save(new DeliverySlotCounter(oldCounter.id(), oldSlotId, date, 50, 9));
        // Verifies old lock released
        verify(deliverySlotLockRepository).updateStatus(oldLock.id(), DeliverySlotLockStatus.RELEASED);
        // Verifies new counter incremented
        verify(deliverySlotCounterRepository).save(new DeliverySlotCounter(newCounter.id(), newSlotId, date, 50, 1));
    }

    @Test
    void consumeLock_activeLock_marksConsumedWithoutDecrement() {
        UUID lockId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DeliverySlotLock lock = new DeliverySlotLock(lockId, userId, UUID.randomUUID(), LocalDate.now(), Instant.now().plusSeconds(60), DeliverySlotLockStatus.ACTIVE);
        when(deliverySlotLockRepository.findById(lockId)).thenReturn(Optional.of(lock));

        deliverySlotService.consumeLock(lockId, userId);

        verify(deliverySlotLockRepository).updateStatus(lockId, DeliverySlotLockStatus.CONSUMED);
        verify(deliverySlotCounterRepository, never()).save(any());
    }

    @Test
    void consumeLock_alreadyReleased_noop() {
        UUID lockId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DeliverySlotLock lock = new DeliverySlotLock(lockId, userId, UUID.randomUUID(), LocalDate.now(), Instant.now().plusSeconds(60), DeliverySlotLockStatus.RELEASED);
        when(deliverySlotLockRepository.findById(lockId)).thenReturn(Optional.of(lock));

        deliverySlotService.consumeLock(lockId, userId);

        verify(deliverySlotLockRepository, org.mockito.Mockito.never()).updateStatus(any(), any());
    }

    @Test
    void consumeLock_wrongUser_throwsUnauthorized() {
        UUID lockId = UUID.randomUUID();
        DeliverySlotLock lock = new DeliverySlotLock(lockId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), Instant.now().plusSeconds(60), DeliverySlotLockStatus.ACTIVE);
        when(deliverySlotLockRepository.findById(lockId)).thenReturn(Optional.of(lock));

        assertThatThrownBy(() -> deliverySlotService.consumeLock(lockId, UUID.randomUUID()))
                .isInstanceOf(com.builddash.backend.domain.exception.UnauthorizedException.class);
    }
}