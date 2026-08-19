package com.builddash.backend.domain.port;

import java.util.UUID;

/**
 * OCP: the transport (RabbitMQ today) is hidden behind this port, same abstraction level as
 * OtpDispatchQueue — no queue/exchange name ever leaks past the adapter.
 */
public interface CatalogEventPublisher {

    /**
     * Publishes with a synchronous publisher-confirm wait. Returns false on nack/timeout — a
     * nack is an expected, branchable outcome (the outbox row just stays PENDING for the next
     * poll), not an exceptional one.
     */
    boolean publishProductChanged(UUID correlationId, String payloadJson);
}
