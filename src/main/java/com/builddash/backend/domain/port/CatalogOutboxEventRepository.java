package com.builddash.backend.domain.port;

import com.builddash.backend.domain.enums.OutboxStatus;
import com.builddash.backend.domain.model.CatalogOutboxEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CatalogOutboxEventRepository {

    CatalogOutboxEvent save(CatalogOutboxEvent event);

    List<CatalogOutboxEvent> findByStatus(OutboxStatus status);

    List<CatalogOutboxEvent> findPendingForRelay(int maxAttempts, int limit);

    /** Bulk update, not fetch-then-mutate — never relies on JPA dirty-checking. */
    void markPublished(UUID id);

    /** Bulk update — set once the search side confirms the ES upsert actually succeeded. */
    void markProcessed(UUID id);

    void recordAttempt(UUID id, int attemptCount, Instant lastAttemptAt, String errorMessage, OutboxStatus status);
}
