package com.builddash.backend.domain.enums;

/**
 * Durable-claim lifecycle (refund-gateway discipline): PENDING is committed
 * before ObjectStorage.store; a conditional UPDATE finalizes to STORED. STORED
 * is terminal — a new upload 409s; a surviving PENDING claim is only ever
 * completed by the explicit retry operation that names it.
 */
public enum PoAttachmentStatus {
    PENDING,
    STORED
}
