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
public class MarginRule {

    private UUID id;
    private UUID productId;
    private UUID categoryId;
    private BigDecimal costPrice;
    private BigDecimal floorPercent;
    private BigDecimal floorPrice;
    private Instant createdAt;
    private Instant updatedAt;
}
