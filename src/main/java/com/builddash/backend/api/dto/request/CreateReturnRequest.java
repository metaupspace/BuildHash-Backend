package com.builddash.backend.api.dto.request;

import com.builddash.backend.domain.enums.ReturnReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateReturnRequest(
        @NotNull(message = "Return reason is required")
        ReturnReason reason,

        @NotEmpty(message = "At least one line item must be specified for return")
        @Valid
        List<ReturnLineItemRequest> lineItems
) {}
