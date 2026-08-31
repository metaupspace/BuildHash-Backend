package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.model.Rfq;
import com.builddash.backend.domain.model.RfqItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RfqResponse(
        UUID id,
        UUID companyId,
        UUID createdByUserId,
        String status,
        Instant expiresAt,
        String notes,
        List<Item> items,
        List<UUID> routedVendorIds,
        Instant createdAt
) {

    public static RfqResponse from(Rfq rfq) {
        return new RfqResponse(
                rfq.id(),
                rfq.companyId(),
                rfq.createdByUserId(),
                rfq.status().name(),
                rfq.expiresAt(),
                rfq.notes(),
                rfq.items().stream().map(Item::from).toList(),
                rfq.routedVendorIds(),
                rfq.createdAt());
    }

    public record Item(
            UUID id,
            UUID productId,
            int quantity
    ) {

        public static Item from(RfqItem item) {
            return new Item(item.id(), item.productId(), item.quantity());
        }
    }
}
