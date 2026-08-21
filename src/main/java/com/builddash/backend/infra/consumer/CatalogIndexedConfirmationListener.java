package com.builddash.backend.infra.consumer;

import com.builddash.backend.domain.port.CatalogOutboxEventRepository;
import com.builddash.backend.infra.config.CatalogQueueConfig;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The confirmation half of PLAN_PHASE1.md Section 3 step 5 — flips the outbox row's terminal
 * state to PROCESSED, which is what makes it "searchable" rather than just "handed to the
 * broker". A dropped confirmation just leaves the row at PUBLISHED for 3c's nightly
 * reconciliation sweep to catch, not a silent loss.
 */
@Component
public class CatalogIndexedConfirmationListener {

    private final CatalogOutboxEventRepository catalogOutboxEventRepository;

    public CatalogIndexedConfirmationListener(CatalogOutboxEventRepository catalogOutboxEventRepository) {
        this.catalogOutboxEventRepository = catalogOutboxEventRepository;
    }

    @RabbitListener(queues = CatalogQueueConfig.INDEXED_QUEUE_NAME)
    public void onMessage(Message message) {
        String outboxEventId = (String) message.getMessageProperties().getHeaders().get(CatalogQueueConfig.OUTBOX_EVENT_ID_HEADER);
        catalogOutboxEventRepository.markProcessed(UUID.fromString(outboxEventId));
    }
}
