package com.builddash.backend.api.dto.request;

import com.builddash.backend.domain.enums.SupportTicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSupportTicketRequest(
        @NotNull(message = "Category is required")
        SupportTicketCategory category,

        @NotBlank(message = "Subject is required")
        @Size(max = 255, message = "Subject must be at most 255 characters")
        String subject,

        @NotBlank(message = "Message is required")
        @Size(max = 5000, message = "Message must be at most 5000 characters")
        String message
) {}
