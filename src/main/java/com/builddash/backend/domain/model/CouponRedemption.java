package com.builddash.backend.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CouponRedemption {

    private UUID id;
    private UUID couponId;
    private UUID userId;
    private UUID orderId;
    private Instant redeemedAt;
}
