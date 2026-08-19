package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.OutboxStatus;
import com.builddash.backend.domain.model.CatalogOutboxEvent;
import com.builddash.backend.domain.port.CatalogEventPublisher;
import com.builddash.backend.domain.port.CatalogOutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * No separate interface — single workflow, single caller (the scheduler), same judgment as
 * OtpSendService. Per-row try/catch so one failing publish never blocks the batch or loses a
 * sibling row still PENDING.
 */
@Service
public class CatalogOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(CatalogOutboxRelay.class);

    private final CatalogOutboxEventRepository catalogOutboxEventRepository;
    private final CatalogEventPublisher catalogEventPublisher;

    public CatalogOutboxRelay(CatalogOutboxEventRepository catalogOutboxEventRepository,
                               CatalogEventPublisher catalogEventPublisher) {
        this.catalogOutboxEventRepository = catalogOutboxEventRepository;
        this.catalogEventPublisher = catalogEventPublisher;
    }

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
