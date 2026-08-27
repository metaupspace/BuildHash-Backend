package com.builddash.backend.application.impl;

import com.builddash.backend.application.event.OrderCancelledEvent;
import com.builddash.backend.application.event.OrderDeliveredEvent;
import com.builddash.backend.application.event.OrderDispatchedEvent;
import com.builddash.backend.application.event.OrderPackedEvent;
import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.application.service.OrderTrackingBroadcaster;
import com.builddash.backend.domain.enums.DeliverySlotLockStatus;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.DeliverySlotLock;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.CallProxyGateway;
import com.builddash.backend.domain.port.DeliveryTrackingEventRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.infra.config.DeliveryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 7 Checkpoint A event-publish proofs for OrderTrackingServiceImpl: every forward transition
 * and both cancel paths fire exactly one ids-only event with the right origin; the no-event paths
 * (same-status redelivery, reschedule, call-driver) publish nothing.
 */
@ExtendWith(MockitoExtension.class)
class OrderTrackingEventPublishTest {

    private static final String API_KEY = "test-webhook-key";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DeliveryTrackingEventRepository trackingEventRepository;

    @Mock
    private DeliverySlotService deliverySlotService;

    @Mock
    private CallProxyGateway callProxyGateway;

    @Mock
    private OrderTrackingBroadcaster broadcaster;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrderTrackingServiceImpl service;

    @BeforeEach
    void setUp() {
        DeliveryProperties properties = new DeliveryProperties();
        properties.setWebhookApiKey(API_KEY);
        service = new OrderTrackingServiceImpl(
                orderRepository, trackingEventRepository, deliverySlotService, callProxyGateway,
                properties, broadcaster, eventPublisher);
    }

    private Order orderIn(OrderStatus status) {
        return new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), BigDecimal.TEN, status, UUID.randomUUID(), Instant.now(), null, null, List.of());
    }

    private void stubOrder(Order order) {
        when(orderRepository.findByIdForUpdate(order.id())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void updateStatus(Order order, OrderStatus target) {
        service.updateDeliveryStatus(order.id(), target, null, null, null, null, API_KEY);
    }

    @Test
    void confirmedToPacked_firesOrderPackedEvent() {
        Order order = orderIn(OrderStatus.CONFIRMED);
        stubOrder(order);

        updateStatus(order, OrderStatus.PACKED);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        OrderPackedEvent event = (OrderPackedEvent) captor.getValue();
        assertThat(event.orderId()).isEqualTo(order.id());
    }

    @Test
    void packedToDispatched_firesOrderDispatchedEvent() {
        Order order = orderIn(OrderStatus.PACKED);
        stubOrder(order);

        updateStatus(order, OrderStatus.DISPATCHED);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        OrderDispatchedEvent event = (OrderDispatchedEvent) captor.getValue();
        assertThat(event.orderId()).isEqualTo(order.id());
    }

    @Test
    void dispatchedToDelivered_firesOrderDeliveredEvent() {
        Order order = orderIn(OrderStatus.DISPATCHED);
        stubOrder(order);

        updateStatus(order, OrderStatus.DELIVERED);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        OrderDeliveredEvent event = (OrderDeliveredEvent) captor.getValue();
        assertThat(event.orderId()).isEqualTo(order.id());
    }

    @Test
    void cancelledViaDeliveryWebhook_firesOrderCancelledWithDeliveryOrigin() {
        Order order = orderIn(OrderStatus.PACKED);
        stubOrder(order);

        updateStatus(order, OrderStatus.CANCELLED);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        OrderCancelledEvent event = (OrderCancelledEvent) captor.getValue();
        assertThat(event.orderId()).isEqualTo(order.id());
        assertThat(event.origin()).isEqualTo(OrderCancelledEvent.OrderCancellationOrigin.DELIVERY_WEBHOOK);
    }

    @Test
    void cancelledWithinWindow_firesOrderCancelledWithCustomerWindowOrigin() {
        Order order = orderIn(OrderStatus.CONFIRMED);
        when(orderRepository.findByIdForUpdate(order.id())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelOrderWithinWindow(order.userId(), order.id());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        OrderCancelledEvent event = (OrderCancelledEvent) captor.getValue();
        assertThat(event.orderId()).isEqualTo(order.id());
        assertThat(event.origin()).isEqualTo(OrderCancelledEvent.OrderCancellationOrigin.CUSTOMER_WINDOW);
    }

    @Test
    void sameStatusRedelivery_withoutCoordsOrDriver_publishesNothing() {
        Order order = orderIn(OrderStatus.PACKED);
        when(orderRepository.findByIdForUpdate(order.id())).thenReturn(Optional.of(order));

        service.updateDeliveryStatus(order.id(), OrderStatus.PACKED, null, null, null, null, API_KEY);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void sameStatusRedelivery_withCoords_publishesNothing() {
        Order order = orderIn(OrderStatus.PACKED);
        when(orderRepository.findByIdForUpdate(order.id())).thenReturn(Optional.of(order));

        service.updateDeliveryStatus(order.id(), OrderStatus.PACKED, null, null, 12.34, 56.78, API_KEY);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void sameStatusRedelivery_withDriverOnly_publishesNothing() {
        Order order = orderIn(OrderStatus.PACKED);
        when(orderRepository.findByIdForUpdate(order.id())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateDeliveryStatus(order.id(), OrderStatus.PACKED, "driver-1", "9999999999", null, null, API_KEY);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void reschedule_publishesNothing() {
        Order order = orderIn(OrderStatus.CONFIRMED);
        when(orderRepository.findByIdForUpdate(order.id())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID newSlotId = UUID.randomUUID();
        LocalDate newSlotDate = LocalDate.now().plusDays(1);
        DeliverySlotLock newLock = new DeliverySlotLock(UUID.randomUUID(), order.userId(), newSlotId,
                newSlotDate, Instant.now().plusSeconds(3600), DeliverySlotLockStatus.CONSUMED);
        when(deliverySlotService.swapConsumedLock(order.userId(), order.deliverySlotLockId(),
                order.slotId(), order.slotDate(), newSlotId, newSlotDate)).thenReturn(newLock);

        service.rescheduleOrder(order.userId(), order.id(), newSlotId, newSlotDate);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void callDriver_publishesNothing() {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), BigDecimal.TEN, OrderStatus.DISPATCHED, UUID.randomUUID(), Instant.now(),
                "driver-1", "9999999999", List.of());
        when(orderRepository.findById(order.id())).thenReturn(Optional.of(order));

        service.callDriver(order.userId(), order.id());

        verify(callProxyGateway).initiateCall(order.id(), order.userId(), "9999999999");
        verify(eventPublisher, never()).publishEvent(any());
    }
}
