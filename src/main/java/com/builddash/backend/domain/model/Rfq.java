package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.RfqStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.builddash.backend.domain.exception.InvalidRfqStateException;

/**
 * A company's request for quotation. Items are immutable after creation and
 * carry no site/project scope (locked OQ-3): they reference catalog products
 * only. Routing is a creation-time snapshot in rfq_routes — never recalculated.
 */
public record Rfq(
        UUID id,
        UUID companyId,
        UUID createdByUserId,
        RfqStatus status,
        Instant expiresAt,
        String notes,
        List<RfqItem> items,
        List<UUID> routedVendorIds,
        Instant createdAt,
        Instant updatedAt
) {

    public Rfq {
        items = items == null ? List.of() : List.copyOf(items);
        routedVendorIds = routedVendorIds == null ? List.of() : List.copyOf(routedVendorIds);
    }

    public boolean isOpen() {
        return status == RfqStatus.OPEN;
    }

    public Rfq withStatus(RfqStatus newStatus) {
        if (status == newStatus) return this;
        if (status != RfqStatus.OPEN) throw InvalidRfqStateException.invalidTransition(status.name(), newStatus.name());
        return new Rfq(id, companyId, createdByUserId, newStatus, expiresAt, notes,
                items, routedVendorIds, createdAt, updatedAt);
    }
}
