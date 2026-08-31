package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.PoAttachmentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A B2B order's PO document claim. id and storageKey are generated at claim
 * time and never change: the unique row permanently owns its object key, so an
 * explicit retry (POST /orders/{orderId}/po/{attachmentId}/retry) overwrites the
 * same key idempotently and the conditional PENDING->STORED finalize decides
 * the single winner. The fresh-upload endpoint never touches an existing claim.
 */
public record PoAttachment(
        UUID id,
        UUID orderId,
        String storageKey,
        String contentType,
        int byteSize,
        UUID uploadedBy,
        PoAttachmentStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public boolean pending() {
        return status == PoAttachmentStatus.PENDING;
    }

    public PoAttachment stored() {
        return new PoAttachment(id, orderId, storageKey, contentType, byteSize,
                uploadedBy, PoAttachmentStatus.STORED, createdAt, updatedAt);
    }
}
