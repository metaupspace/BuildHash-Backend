package com.builddash.backend.domain.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository {

    /**
     * Rolling-window read (PLAN_PHASE8 decision 10): a key older than {@code createdAfter}
     * is treated as not found — the caller creates a genuinely new order. Correctness never
     * depends on the purge sweep having run.
     */
    Optional<UUID> findOrderId(String key, Instant createdAfter);

    void save(String key, UUID orderId);

    /** Nightly hygiene sweep: deletes expired keys, returns the removed row count. */
    int deleteCreatedBefore(Instant cutoff);
}
