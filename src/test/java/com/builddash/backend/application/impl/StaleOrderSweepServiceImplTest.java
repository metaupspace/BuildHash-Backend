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
    private StaleOrderSweepServiceImpl sweepService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        deliverySlotService = mock(DeliverySlotService.class);
        sweepService = new StaleOrderSweepServiceImpl(orderRepository, deliverySlotService); //
    }

    @Test
    void sweepOrder_cancelsOrderAndReleasesLock() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID lockId = UUID.randomUUID();
        
        Order order = new Order(orderId, userId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), BigDecimal.TEN, OrderStatus.PAYMENT_PENDING, lockId, List.of());
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        sweepService.sweepOrder(orderId);

        verify(orderRepository).save(any(Order.class));
        verify(deliverySlotService).releaseLock(lockId, userId);
    }
}
