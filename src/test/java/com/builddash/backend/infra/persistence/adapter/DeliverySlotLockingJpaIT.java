package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.domain.enums.DeliverySlotLockStatus;
import com.builddash.backend.domain.exception.SlotUnavailableException;
import com.builddash.backend.domain.model.DeliverySlotCounter;
import com.builddash.backend.domain.model.DeliverySlotLock;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.DeliverySlotCounterRepository;
import com.builddash.backend.domain.port.DeliverySlotLockRepository;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.infra.persistence.entity.SlotConfigurationEntity;
import com.builddash.backend.infra.persistence.repository.SlotConfigurationJpaRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliverySlotLockingJpaIT extends AbstractIntegrationTest {

    @Autowired
    private DeliverySlotService deliverySlotService;

    @Autowired
    private SlotConfigurationJpaRepository slotConfigurationJpaRepository;

    @Autowired
    private DeliverySlotCounterRepository deliverySlotCounterRepository;

    @Autowired
    private DeliverySlotLockRepository deliverySlotLockRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void concurrentSlotLocking_enforcesCapacityStrictlyUnderRace() throws InterruptedException {
        // 1. Create slot config with small capacity = 2
        UUID slotId = UUID.randomUUID();
        SlotConfigurationEntity config = new SlotConfigurationEntity();
        config.setId(slotId);
        config.setStartTime(LocalTime.of(10, 0));
        config.setEndTime(LocalTime.of(13, 0));
        config.setCapacity(2);
        config.setActive(true);
        slotConfigurationJpaRepository.save(config);

        LocalDate testDate = LocalDate.of(2026, 10, 15);
        DeliverySlotCounter counter = new DeliverySlotCounter(UUID.randomUUID(), slotId, testDate, 2, 0);
        deliverySlotCounterRepository.save(counter);

        // 2. Create 10 distinct users
        int numThreads = 10;
        List<UUID> userIds = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            User u = new User();
            u.setPhone("+9199990000" + i);
            User saved = userRepository.save(u);
            userIds.add(saved.getId());
        }

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numThreads; i++) {
            final UUID uid = userIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    deliverySlotService.acquireOrSwapLock(uid, slotId, testDate, Duration.ofMinutes(10));
                    successCount.incrementAndGet();
                } catch (SlotUnavailableException e) {
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(exceptions).isEmpty();
        assertThat(successCount.get()).isEqualTo(2);
        assertThat(failCount.get()).isEqualTo(8);

        DeliverySlotCounter finalCounter = deliverySlotCounterRepository.findBySlotIdAndSlotDate(slotId, testDate).orElseThrow();
        assertThat(finalCounter.currentCount()).isEqualTo(2);
    }

    @Test
    void failedSwap_rollsBackAtomically_preservingPriorLock() {
        // Create full slot (capacity 1, count 1) and user holding old slot (capacity 1, count 1)
        UUID oldSlotId = UUID.randomUUID();
        SlotConfigurationEntity oldConfig = new SlotConfigurationEntity();
        oldConfig.setId(oldSlotId);
        oldConfig.setStartTime(LocalTime.of(9, 0));
        oldConfig.setEndTime(LocalTime.of(12, 0));
        oldConfig.setCapacity(1);
        oldConfig.setActive(true);
        slotConfigurationJpaRepository.save(oldConfig);

        UUID fullSlotId = UUID.randomUUID();
        SlotConfigurationEntity fullConfig = new SlotConfigurationEntity();
        fullConfig.setId(fullSlotId);
        fullConfig.setStartTime(LocalTime.of(14, 0));
        fullConfig.setEndTime(LocalTime.of(17, 0));
        fullConfig.setCapacity(1);
        fullConfig.setActive(true);
        slotConfigurationJpaRepository.save(fullConfig);

        LocalDate testDate = LocalDate.of(2026, 10, 16);
        deliverySlotCounterRepository.save(new DeliverySlotCounter(UUID.randomUUID(), oldSlotId, testDate, 1, 0));
        deliverySlotCounterRepository.save(new DeliverySlotCounter(UUID.randomUUID(), fullSlotId, testDate, 1, 1)); // full!

        User u = new User();
        u.setPhone("+919999000099");
        User savedUser = userRepository.save(u);

        // User acquires old slot
        DeliverySlotLock oldLock = deliverySlotService.acquireOrSwapLock(savedUser.getId(), oldSlotId, testDate, Duration.ofMinutes(10));
        assertThat(oldLock.status()).isEqualTo(DeliverySlotLockStatus.ACTIVE);

        // Attempt to swap to full slot - must fail
        assertThatThrownBy(() -> deliverySlotService.acquireOrSwapLock(savedUser.getId(), fullSlotId, testDate, Duration.ofMinutes(10)))
                .isInstanceOf(SlotUnavailableException.class);

        // Old slot counter must still be 1, old lock must still be ACTIVE
        DeliverySlotCounter oldCounter = deliverySlotCounterRepository.findBySlotIdAndSlotDate(oldSlotId, testDate).orElseThrow();
        assertThat(oldCounter.currentCount()).isEqualTo(1);

        DeliverySlotLock currentActiveLock = deliverySlotLockRepository.findActiveByUserId(savedUser.getId(), java.time.Instant.now()).orElseThrow();
        assertThat(currentActiveLock.id()).isEqualTo(oldLock.id());
        assertThat(currentActiveLock.slotId()).isEqualTo(oldSlotId);
    }
}
