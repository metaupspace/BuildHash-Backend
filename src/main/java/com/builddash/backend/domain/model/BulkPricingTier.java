package com.builddash.backend.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BulkPricingTier {

    private UUID id;
    private UUID productId;
    private int minQuantity;
    private BigDecimal unitPrice;
    private Instant createdAt;
    private Instant updatedAt;
}
