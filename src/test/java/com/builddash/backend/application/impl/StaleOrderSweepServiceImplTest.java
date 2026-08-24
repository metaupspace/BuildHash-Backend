package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaleOrderSweepServiceImplTest {

    private OrderRepository orderRepository;
    private DeliverySlotService deliverySlotService;
    private com.builddash.backend.domain.port.DeliverySlotLockRepository deliverySlotLockRepository;
    private com.builddash.backend.domain.port.DeliverySlotCounterRepository deliverySlotCounterRepository;
    private StaleOrderSweepServiceImpl sweepService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        deliverySlotService = mock(DeliverySlotService.class);
        deliverySlotLockRepository = mock(com.builddash.backend.domain.port.DeliverySlotLockRepository.class);
        deliverySlotCounterRepository = mock(com.builddash.backend.domain.port.DeliverySlotCounterRepository.class);
        sweepService = new StaleOrderSweepServiceImpl(orderRepository, deliverySlotService,
                deliverySlotLockRepository, deliverySlotCounterRepository);
        // Self-injection is a Spring field injection; wire it manually for the unit test
        try {
            java.lang.reflect.Field selfField = StaleOrderSweepServiceImpl.class.getDeclaredField("self");
            selfField.setAccessible(true);
            selfField.set(sweepService, sweepService);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void sweepOrder_cancelsOrderAndReleasesLock() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID lockId = UUID.randomUUID();

        Order order = new Order(orderId, userId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), BigDecimal.TEN, OrderStatus.PAYMENT_PENDING, lockId, java.time.Instant.now(), null, null, List.of());
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        sweepService.sweepOrder(orderId);

        verify(orderRepository).save(any(Order.class));
        verify(deliverySlotService).releaseLock(lockId, userId);
    }

    @Test
    void sweepExpiredLocks_decrementsCounterAndMarksExpired() {
        UUID lockId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        LocalDate date = LocalDate.now();
        com.builddash.backend.domain.model.DeliverySlotLock expiredLock =
                new com.builddash.backend.domain.model.DeliverySlotLock(lockId, userId, slotId, date, Instant.now().minusSeconds(60), com.builddash.backend.domain.enums.DeliverySlotLockStatus.ACTIVE);
        when(deliverySlotLockRepository.findExpiredActiveLocks(any())).thenReturn(List.of(expiredLock));

        com.builddash.backend.domain.model.DeliverySlotCounter counter =
                new com.builddash.backend.domain.model.DeliverySlotCounter(UUID.randomUUID(), slotId, date, 5, 3);
        when(deliverySlotCounterRepository.findBySlotIdAndSlotDateForUpdate(slotId, date)).thenReturn(Optional.of(counter));

        sweepService.sweepExpiredLocks();

        verify(deliverySlotCounterRepository).save(any(com.builddash.backend.domain.model.DeliverySlotCounter.class)); // decremented
        verify(deliverySlotLockRepository).updateStatus(lockId, com.builddash.backend.domain.enums.DeliverySlotLockStatus.EXPIRED);
    }

    @Test
    void sweepExpiredLocks_missingCounter_stillMarksExpired() {
        UUID lockId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        LocalDate date = LocalDate.now();
        com.builddash.backend.domain.model.DeliverySlotLock expiredLock =
                new com.builddash.backend.domain.model.DeliverySlotLock(lockId, UUID.randomUUID(), slotId, date, Instant.now().minusSeconds(60), com.builddash.backend.domain.enums.DeliverySlotLockStatus.ACTIVE);
        when(deliverySlotLockRepository.findExpiredActiveLocks(any())).thenReturn(List.of(expiredLock));
        when(deliverySlotCounterRepository.findBySlotIdAndSlotDateForUpdate(slotId, date)).thenReturn(Optional.empty());

        sweepService.sweepExpiredLocks();

        verify(deliverySlotLockRepository).updateStatus(lockId, com.builddash.backend.domain.enums.DeliverySlotLockStatus.EXPIRED);
        verify(deliverySlotCounterRepository, org.mockito.Mockito.never()).save(any());
    }
}
