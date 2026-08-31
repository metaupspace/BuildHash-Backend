package com.builddash.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** name/addressId optional on update (null = leave unchanged). */
public record CompanySiteRequest(
        @NotBlank @Size(max = 200) String name,
        UUID addressId,
        Boolean active
) {
}
