package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.application.service.StaleOrderSweepService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaleOrderSweepServiceImpl implements StaleOrderSweepService {

    private final OrderRepository orderRepository;
    private final DeliverySlotService deliverySlotService;
    private @org.springframework.context.annotation.Lazy @org.springframework.beans.factory.annotation.Autowired StaleOrderSweepServiceImpl self; // Self-injection for REQUIRED_NEW transaction boundary

    @Override
    public void sweepStaleOrders() {
        Instant cutoff = Instant.now();
        List<UUID> staleOrderIds = orderRepository.findStalePaymentPendingOrderIds(cutoff);

        if (staleOrderIds.isEmpty()) {
            return;
        }
        
        log.info("Found {} stale PAYMENT_PENDING orders", staleOrderIds.size());

        for (UUID orderId : staleOrderIds) {
            try {
                self.sweepOrder(orderId);
            } catch (Exception e) {
                log.error("Failed to sweep order {}", orderId, e);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sweepOrder(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElse(null);

        if (order == null || order.status() != OrderStatus.PAYMENT_PENDING) {
            return;
        }

        log.info("Cancelling stale order {}", orderId);
        Order cancelled = order.cancel();
        orderRepository.save(cancelled);

        try {
            deliverySlotService.releaseLock(cancelled.deliverySlotLockId(), cancelled.userId());
        } catch (Exception e) {
            log.warn("Could not release slot lock {} for order {}", cancelled.deliverySlotLockId(), orderId, e);
        }
    }
}
