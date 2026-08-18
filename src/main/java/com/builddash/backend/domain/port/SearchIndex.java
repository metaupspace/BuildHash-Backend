package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.ProductSyncPayload;

/**
 * Adapter isolates the search vendor (Elasticsearch today) — nothing above this port knows
 * about ES types or the "products" alias by name.
 */
public interface SearchIndex {

    /**
     * Upserts with external versioning (payload.updatedAtEpochMillis() as the version) — a
     * stale write (lower version than what's stored) is expected under out-of-order delivery
     * and must be silently ignored, not treated as a failure.
     */
    void upsertProduct(ProductSyncPayload payload);
}
