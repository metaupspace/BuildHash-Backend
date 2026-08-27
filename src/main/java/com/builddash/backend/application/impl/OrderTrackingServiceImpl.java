package com.builddash.backend.application.impl;

import com.builddash.backend.application.event.OrderCancelledEvent;
import com.builddash.backend.application.event.OrderDeliveredEvent;
import com.builddash.backend.application.event.OrderDispatchedEvent;
import com.builddash.backend.application.event.OrderPackedEvent;
import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.application.service.OrderTrackingBroadcaster;
import com.builddash.backend.application.service.OrderTrackingService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.exception.BadRequestException;
import com.builddash.backend.domain.exception.InvalidOrderStateException;
import com.builddash.backend.domain.exception.ModificationWindowExpiredException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.UnauthorizedException;
import com.builddash.backend.domain.model.DeliverySlotLock;
import com.builddash.backend.domain.model.DeliveryTrackingEvent;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderTracking;
import com.builddash.backend.domain.port.CallProxyGateway;
import com.builddash.backend.domain.port.DeliveryTrackingEventRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.infra.config.DeliveryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderTrackingServiceImpl implements OrderTrackingService {

    private final OrderRepository orderRepository;
    private final DeliveryTrackingEventRepository trackingEventRepository;
    private final DeliverySlotService deliverySlotService;
    private final CallProxyGateway callProxyGateway;
    private final DeliveryProperties deliveryProperties;
    private final OrderTrackingBroadcaster broadcaster;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void updateDeliveryStatus(UUID orderId, OrderStatus status, String driverId, String driverPhone,
                                     Double latitude, Double longitude, String apiKey) {
        String configuredKey = deliveryProperties.getWebhookApiKey();
        if (configuredKey == null || configuredKey.isBlank() || !configuredKey.equals(apiKey)) {
            throw new UnauthorizedException("UNAUTHORIZED", "Invalid or missing webhook API key");
        }

        if (latitude != null && (latitude < -90.0 || latitude > 90.0)) {
            throw new BadRequestException("INVALID_COORDINATES", "Latitude must be between -90 and 90");
        }
        if (longitude != null && (longitude < -180.0 || longitude > 180.0)) {
            throw new BadRequestException("INVALID_COORDINATES", "Longitude must be between -180 and 180");
        }

        // Row-locked load: prevents a lost-update race against concurrent status writers
        // (sweep, reschedule) — same discipline as retryPayment/StaleOrderSweepServiceImpl
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Order", orderId.toString()));

        OrderStatus currentStatus = order.status();

        if (status == currentStatus && status != OrderStatus.CANCELLED) {
            // Idempotent same-status redelivery
            Optional<DeliveryTrackingEvent> latestEventOpt = trackingEventRepository.findLatestByOrderId(orderId);
            boolean moved = false;
            if (latitude != null && longitude != null) {
                if (latestEventOpt.isEmpty()) {
                    moved = true;
                } else {
                    DeliveryTrackingEvent prev = latestEventOpt.get();
                    moved = prev.latitude() == null || prev.longitude() == null
                            || !prev.latitude().equals(latitude) || !prev.longitude().equals(longitude);
                }
            }

            if (moved) {
                trackingEventRepository.save(new DeliveryTrackingEvent(
                        UUID.randomUUID(), orderId, currentStatus, latitude, longitude, Instant.now()));
            }

            if (driverId != null || driverPhone != null) {
                order = order.updateDriver(driverId, driverPhone);
                orderRepository.save(order);
            }
        } else {
            // State transition
            if (currentStatus == OrderStatus.CONFIRMED && status == OrderStatus.PACKED) {
                order = order.pack();
            } else if (currentStatus == OrderStatus.PACKED && status == OrderStatus.DISPATCHED) {
                order = order.dispatch(driverId, driverPhone);
            } else if (currentStatus == OrderStatus.DISPATCHED && status == OrderStatus.DELIVERED) {
                order = order.deliver();
            } else if (status == OrderStatus.CANCELLED
                    && (currentStatus == OrderStatus.CONFIRMED
                        || currentStatus == OrderStatus.PACKED
                        || currentStatus == OrderStatus.DISPATCHED)) {
                order = order.cancelFromDelivery();
            } else {
                throw new InvalidOrderStateException(currentStatus.name(), status.name());
            }

            if (driverId != null || driverPhone != null) {
                order = order.updateDriver(driverId, driverPhone);
            }

            orderRepository.save(order);
            trackingEventRepository.save(new DeliveryTrackingEvent(
                    UUID.randomUUID(), orderId, status, latitude, longitude, Instant.now()));

            // Phase 7 notification triggers — publish-only addition, no transition-behavior change
            switch (status) {
                case PACKED -> eventPublisher.publishEvent(new OrderPackedEvent(orderId));
                case DISPATCHED -> eventPublisher.publishEvent(new OrderDispatchedEvent(orderId));
                case DELIVERED -> eventPublisher.publishEvent(new OrderDeliveredEvent(orderId));
                case CANCELLED -> eventPublisher.publishEvent(new OrderCancelledEvent(
                        orderId, OrderCancelledEvent.OrderCancellationOrigin.DELIVERY_WEBHOOK));
                default -> { /* no notification event for other statuses */ }
            }
        }

        Optional<DeliveryTrackingEvent> latestEvent = trackingEventRepository.findLatestByOrderId(orderId);
        Double lat = latestEvent.map(DeliveryTrackingEvent::latitude).orElse(latitude);
        Double lng = latestEvent.map(DeliveryTrackingEvent::longitude).orElse(longitude);
        Instant updatedAt = latestEvent.map(DeliveryTrackingEvent::recordedAt).orElse(Instant.now());

        OrderTracking tracking = new OrderTracking(
                orderId,
                order.status(),
                order.driverId(),
                order.driverPhone(),
                lat,
                lng,
                updatedAt
        );

        broadcaster.broadcast(orderId, tracking);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderTracking getTracking(UUID userId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.userId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Order", orderId.toString()));

        Optional<DeliveryTrackingEvent> latest = trackingEventRepository.findLatestByOrderId(orderId);
        Double lat = latest.map(DeliveryTrackingEvent::latitude).orElse(null);
        Double lng = latest.map(DeliveryTrackingEvent::longitude).orElse(null);
        Instant updatedAt = latest.map(DeliveryTrackingEvent::recordedAt).orElse(order.placedAt());

        return new OrderTracking(
                orderId,
                order.status(),
                order.driverId(),
                order.driverPhone(),
                lat,
                lng,
                updatedAt
        );
    }

    @Override
    @Transactional
    public void rescheduleOrder(UUID userId, UUID orderId, UUID newSlotId, LocalDate newSlotDate) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .filter(o -> o.userId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Order", orderId.toString()));

        if (order.status() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(order.status().name(), "RESCHEDULED");
        }

        Instant windowEnd = order.placedAt().plus(Duration.ofMinutes(deliveryProperties.getModificationWindowMinutes()));
        if (Instant.now().isAfter(windowEnd)) {
            throw new ModificationWindowExpiredException();
        }

        if (newSlotId.equals(order.slotId()) && newSlotDate.equals(order.slotDate())) {
            return;
        }

        DeliverySlotLock newLock = deliverySlotService.swapConsumedLock(
                userId, order.deliverySlotLockId(), order.slotId(), order.slotDate(), newSlotId, newSlotDate);

        Order updated = order.reschedule(newSlotId, newSlotDate, newLock.id());
        orderRepository.save(updated);
    }

    @Override
    @Transactional
    public void cancelOrderWithinWindow(UUID userId, UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .filter(o -> o.userId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Order", orderId.toString()));

        if (order.status() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(order.status().name(), OrderStatus.CANCELLED.name());
        }

        Instant windowEnd = order.placedAt().plus(Duration.ofMinutes(deliveryProperties.getModificationWindowMinutes()));
        if (Instant.now().isAfter(windowEnd)) {
            throw new ModificationWindowExpiredException();
        }

        deliverySlotService.releaseConsumedLock(order.deliverySlotLockId(), order.slotId(), order.slotDate());

        Order updated = order.cancelConfirmed();
        orderRepository.save(updated);
        eventPublisher.publishEvent(new OrderCancelledEvent(
                orderId, OrderCancelledEvent.OrderCancellationOrigin.CUSTOMER_WINDOW));
    }

    @Override
    @Transactional(readOnly = true)
    public void callDriver(UUID userId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.userId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Order", orderId.toString()));

        if (order.status() != OrderStatus.DISPATCHED || order.driverPhone() == null || order.driverPhone().isBlank()) {
            throw new BadRequestException("DRIVER_UNAVAILABLE", "Driver is not available for call");
        }

        callProxyGateway.initiateCall(orderId, userId, order.driverPhone());
    }
}
