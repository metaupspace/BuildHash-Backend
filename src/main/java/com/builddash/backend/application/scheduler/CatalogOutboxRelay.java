package com.builddash.backend.application.scheduler;

import com.builddash.backend.domain.enums.OutboxStatus;
import com.builddash.backend.domain.model.CatalogOutboxEvent;
import com.builddash.backend.domain.port.CatalogEventPublisher;
import com.builddash.backend.domain.port.CatalogOutboxEventRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * No separate interface — single workflow, single caller (the scheduler), same judgment as
 * OtpSendService. Per-row try/catch so one failing publish never blocks the batch or loses a
 * sibling row still PENDING.
 */
@RequiredArgsConstructor
@Slf4j
@Service
public class CatalogOutboxRelay {

    private final CatalogOutboxEventRepository catalogOutboxEventRepository;
    private final CatalogEventPublisher catalogEventPublisher;


    @Scheduled(fixedDelayString = "${catalog.outbox.relay-interval-ms:5000}")
    public void relay() {
        List<CatalogOutboxEvent> pending = catalogOutboxEventRepository.findByStatus(OutboxStatus.PENDING);
        for (CatalogOutboxEvent event : pending) {
            relayOne(event);
        }
    }

    private void relayOne(CatalogOutboxEvent event) {
        try {
            boolean acked = catalogEventPublisher.publishProductChanged(event.getId(), event.getPayload());
            if (acked) {
                catalogOutboxEventRepository.markPublished(event.getId());
            } else {
                log.warn("Publish nacked/timed out for outbox event {}, will retry next poll", event.getId());
            }
        } catch (Exception e) {
            log.error("Failed to relay outbox event {}, will retry next poll", event.getId(), e);
        }
    }
}
