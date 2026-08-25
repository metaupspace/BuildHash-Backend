package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.OrderTrackingBroadcaster;
import com.builddash.backend.domain.model.OrderTracking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderTrackingBroadcasterImpl implements OrderTrackingBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void broadcast(UUID orderId, OrderTracking tracking) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendSafely(orderId, tracking);
                }
            });
        } else {
            sendSafely(orderId, tracking);
        }
    }

    private void sendSafely(UUID orderId, OrderTracking tracking) {
        try {
            messagingTemplate.convertAndSend("/topic/orders/" + orderId, tracking);
            log.debug("Broadcast tracking update for order {} over WebSocket: status={}", orderId, tracking.status());
        } catch (Exception e) {
            log.error("Failed to broadcast order tracking over WebSocket for orderId {}: {}", orderId, e.getMessage(), e);
        }
    }
}
