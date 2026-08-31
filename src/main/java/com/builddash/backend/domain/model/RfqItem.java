package com.builddash.backend.domain.model;

import java.util.UUID;

/** Immutable RFQ line: catalog product + quantity (> 0). No siteId, no projectId. */
public record RfqItem(
        UUID id,
        UUID rfqId,
        UUID productId,
        int quantity
) {
}
