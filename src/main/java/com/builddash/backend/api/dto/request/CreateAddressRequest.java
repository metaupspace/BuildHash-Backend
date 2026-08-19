package com.builddash.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateAddressRequest(
        @NotBlank String type,
        @NotBlank String line1,
        String line2,
        @NotBlank String city,
        @NotBlank String state,
        @NotBlank String zipCode
) {
}
