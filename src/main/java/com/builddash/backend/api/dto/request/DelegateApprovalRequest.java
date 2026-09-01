package com.builddash.backend.api.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DelegateApprovalRequest(
        @NotNull UUID delegateMemberId
) {
}
