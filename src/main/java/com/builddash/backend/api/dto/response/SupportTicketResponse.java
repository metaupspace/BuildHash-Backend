package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.enums.SupportTicketCategory;
import com.builddash.backend.domain.enums.SupportTicketStatus;

import java.time.Instant;
import java.util.UUID;

public record SupportTicketResponse(
        UUID id,
        SupportTicketCategory category,
        SupportTicketStatus status,
        String subject,
        Instant slaDueAt,
        Instant createdAt,
        Instant updatedAt
) {}
