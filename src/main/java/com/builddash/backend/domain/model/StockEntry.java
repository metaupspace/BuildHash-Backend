package com.builddash.backend.domain.model;

/**
 * STUB for Phase 1 (PLAN_PHASE1.md Open Question #1, resolved): static/manually-seeded
 * quantities only, not wired to any real vendor/warehouse event. No such system exists yet
 * to publish from — building a consumer against an imagined contract now means redoing it
 * once that system is real. Revisit when a real inventory source exists.
 */
public record StockEntry(String warehouseId, int quantity) {
}
