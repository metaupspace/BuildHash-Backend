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
public class ContractPrice {

    private UUID id;
    private UUID userId;
    private UUID productId;
    private BigDecimal unitPrice;
    private Instant effectiveFrom;
    private Instant effectiveTo;
    private Instant createdAt;
    private Instant updatedAt;
}
