package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.OutboxStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CatalogOutboxEvent {

    public static final String EVENT_TYPE_PRODUCT_UPSERTED = "PRODUCT_UPSERTED";

    /**
     * Not yet used — PLAN_PHASE1.md Section 3 leaves the delete event shape as an open "or"
     * (same type with status=inactive, or a distinct type). Pinned here so 3b picks this
     * literal value instead of inventing one ad-hoc.
     */
    public static final String EVENT_TYPE_PRODUCT_DELETED = "catalog.product.deleted";

    private UUID id;
    private UUID productId;
    private String eventType;

    /** The full ES-ready projection (see ProductSyncPayload), serialized to JSON. */
    private String payload;

    private OutboxStatus status = OutboxStatus.PENDING;
    private int attemptCount;
    private Instant lastAttemptAt;
    private String errorMessage;
    private Instant createdAt;
}
