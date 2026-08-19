package com.builddash.backend.domain.model;

import java.util.Map;
import java.util.UUID;

/**
 * The ES-ready projection built once at write time (PLAN_PHASE1.md Section 3) — the search
 * listener upserts this shape directly, never re-reading Postgres to fill in a thin event.
 */
public record ProductSyncPayload(
        UUID productId,
        String name,
        String slug,
        String category,
        String brand,
        Map<String, Object> attributes,
        String stockStatus,
        long updatedAtEpochMillis
) {
}
