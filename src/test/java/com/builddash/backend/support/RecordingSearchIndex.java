package com.builddash.backend.support;

import com.builddash.backend.domain.model.ProductSyncPayload;
import com.builddash.backend.domain.port.SearchIndex;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only SearchIndex that enforces external versioning the same way real Elasticsearch
 * would (version_type=external) — a write with a lower/equal updatedAtEpochMillis than what's
 * stored is silently dropped, not applied. Not a rubber-stamp fake: without this, an
 * out-of-order-delivery test would pass regardless of whether the production code actually
 * gets versioning right.
 */
public class RecordingSearchIndex implements SearchIndex {

    private final Map<UUID, ProductSyncPayload> stored = new ConcurrentHashMap<>();

    @Override
    public void upsertProduct(ProductSyncPayload payload) {
        stored.merge(payload.productId(), payload, (existing, incoming) ->
                incoming.updatedAtEpochMillis() > existing.updatedAtEpochMillis() ? incoming : existing);
    }

    public ProductSyncPayload get(UUID productId) {
        return stored.get(productId);
    }
}
