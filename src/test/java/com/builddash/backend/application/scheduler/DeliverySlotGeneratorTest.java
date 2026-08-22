package com.builddash.backend.application.scheduler;

import com.builddash.backend.domain.model.DeliverySlotCounter;
import com.builddash.backend.domain.model.SlotConfiguration;
import com.builddash.backend.domain.port.DeliverySlotCounterRepository;
import com.builddash.backend.domain.port.SlotConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliverySlotGeneratorTest {

    private SlotConfigurationRepository slotConfigurationRepository;
    private DeliverySlotCounterRepository deliverySlotCounterRepository;
    private DeliverySlotGenerator generator;

    @BeforeEach
    void setUp() {
        slotConfigurationRepository = mock(SlotConfigurationRepository.class);
        deliverySlotCounterRepository = mock(DeliverySlotCounterRepository.class);
        generator = new DeliverySlotGenerator(slotConfigurationRepository, deliverySlotCounterRepository);
    }

    @Test
    void generateSlotsForRange_createsCountersWhenNotExisting() {
        UUID slotId = UUID.randomUUID();
        SlotConfiguration slot = new SlotConfiguration(slotId, LocalTime.of(9, 0), LocalTime.of(12, 0), 50, true);
        LocalDate date = LocalDate.of(2026, 9, 1);

        when(slotConfigurationRepository.findAllActive()).thenReturn(List.of(slot));
        when(deliverySlotCounterRepository.existsBySlotIdAndSlotDate(slotId, date)).thenReturn(false);

        generator.generateSlotsForRange(date, date);

        verify(deliverySlotCounterRepository, times(1)).save(any(DeliverySlotCounter.class));
    }

    @Test
    void generateSlotsForRange_isIdempotent_skipsExistingCounters() {
        UUID slotId = UUID.randomUUID();
        SlotConfiguration slot = new SlotConfiguration(slotId, LocalTime.of(9, 0), LocalTime.of(12, 0), 50, true);
        LocalDate date = LocalDate.of(2026, 9, 1);

        when(slotConfigurationRepository.findAllActive()).thenReturn(List.of(slot));
        when(deliverySlotCounterRepository.existsBySlotIdAndSlotDate(slotId, date)).thenReturn(true);

        generator.generateSlotsForRange(date, date);

        verify(deliverySlotCounterRepository, never()).save(any(DeliverySlotCounter.class));
    }
}
