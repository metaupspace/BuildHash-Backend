package com.builddash.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ApplyCartCouponRequest(
        @NotBlank String couponCode
) {
}
