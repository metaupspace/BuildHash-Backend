package com.builddash.backend.application.scheduler;

import com.builddash.backend.domain.model.DeliverySlotCounter;
import com.builddash.backend.domain.model.SlotConfiguration;
import com.builddash.backend.domain.port.DeliverySlotCounterRepository;
import com.builddash.backend.domain.port.SlotConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Pre-creates DeliverySlotCounter rows for rolling future dates (e.g. next 7 days).
 * Runs nightly or on startup.
 * Idempotency guaranteed by existsBySlotIdAndSlotDate check and DB unique constraint (slot_id, slot_date).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliverySlotGenerator {

    private final SlotConfigurationRepository slotConfigurationRepository;
    private final DeliverySlotCounterRepository deliverySlotCounterRepository;

    @Value("${delivery.slots.days-ahead:7}")
    private int daysAhead = 7;

    @Scheduled(cron = "${delivery.slots.generator.cron:0 5 0 * * *}")
    @Transactional
    public void generateRollingSlots() {
        LocalDate today = LocalDate.now();
        generateSlotsForRange(today, today.plusDays(daysAhead));
    }

    @Transactional
    public void generateSlotsForRange(LocalDate startDate, LocalDate endDate) {
        List<SlotConfiguration> activeSlots = slotConfigurationRepository.findAllActive();
        if (activeSlots.isEmpty()) {
            log.warn("No active slot configurations found when generating delivery slot counters");
            return;
        }

        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            for (SlotConfiguration slot : activeSlots) {
                if (!deliverySlotCounterRepository.existsBySlotIdAndSlotDate(slot.id(), current)) {
                    DeliverySlotCounter counter = new DeliverySlotCounter(
                            UUID.randomUUID(),
                            slot.id(),
                            current,
                            slot.capacity(),
                            0
                    );
                    deliverySlotCounterRepository.save(counter);
                    log.debug("Created delivery slot counter for slot {} on {}", slot.id(), current);
                }
            }
            current = current.plusDays(1);
        }
    }
}
