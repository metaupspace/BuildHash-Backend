package com.builddash.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 20) String gstNumber,
        @Size(max = 254) String statementEmail,
        @Size(max = 40) String businessTimezone
) {
}
