package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.DiscountType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Coupon {

    private UUID id;
    private String code;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private Instant expiresAt;
    private Integer maxUsesPerUser;
    private List<UUID> eligibleCategoryIds = new ArrayList<>();
    private boolean stackable;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
