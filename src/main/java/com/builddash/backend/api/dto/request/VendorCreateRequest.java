package com.builddash.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record VendorCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @NotEmpty List<UUID> categoryIds
) {
}
