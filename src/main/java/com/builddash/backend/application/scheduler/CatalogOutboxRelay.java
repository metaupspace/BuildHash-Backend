package com.builddash.backend.application.scheduler;

import com.builddash.backend.domain.enums.OutboxStatus;
import com.builddash.backend.domain.model.CatalogOutboxEvent;
import com.builddash.backend.domain.port.CatalogEventPublisher;
import com.builddash.backend.domain.port.CatalogOutboxEventRepository;
import com.builddash.backend.application.service.ApplicationMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * H5.2: Bounded batch relay (50 items) with finite retry (5 attempts) and terminal FAILED status.
 * Poison rows never block sibling events.
 */
@RequiredArgsConstructor
@Slf4j
@Service
public class CatalogOutboxRelay {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 5;

    private final CatalogOutboxEventRepository catalogOutboxEventRepository;
    private final CatalogEventPublisher catalogEventPublisher;
    private final ApplicationMetrics metrics;

    @Scheduled(fixedDelayString = "${catalog.outbox.relay-interval-ms:5000}")
    public void relay() {
        List<CatalogOutboxEvent> pending = catalogOutboxEventRepository.findPendingForRelay(MAX_ATTEMPTS, BATCH_SIZE);
        for (CatalogOutboxEvent event : pending) {
            relayOne(event);
        }
    }

    private void relayOne(CatalogOutboxEvent event) {
        int nextAttempt = event.getAttemptCount() + 1;
        try {
            boolean acked = catalogEventPublisher.publishProductChanged(event.getId(), event.getPayload());
            if (acked) {
                catalogOutboxEventRepository.markPublished(event.getId());
            } else {
                log.warn("Publish nacked/timed out for outbox event {}, attempt {}", event.getId(), nextAttempt);
                OutboxStatus nextStatus = nextAttempt >= MAX_ATTEMPTS ? OutboxStatus.FAILED : OutboxStatus.PENDING;
                catalogOutboxEventRepository.recordAttempt(event.getId(), nextAttempt, Instant.now(), "Publish nacked or timed out", nextStatus);
                if (nextStatus == OutboxStatus.FAILED) metrics.recordOutboxFailure("product_changed");
            }
        } catch (Exception e) {
            log.error("Failed to relay outbox event {}, attempt {}", event.getId(), nextAttempt, e);
            OutboxStatus nextStatus = nextAttempt >= MAX_ATTEMPTS ? OutboxStatus.FAILED : OutboxStatus.PENDING;
            catalogOutboxEventRepository.recordAttempt(event.getId(), nextAttempt, Instant.now(), e.getMessage(), nextStatus);
            if (nextStatus == OutboxStatus.FAILED) metrics.recordOutboxFailure("product_changed");
        }
    }
}
