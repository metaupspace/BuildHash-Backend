package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.enums.SupportTicketMessageSender;

import java.time.Instant;
import java.util.UUID;

public record SupportTicketMessageResponse(
        UUID id,
        SupportTicketMessageSender senderRole,
        String body,
        Instant createdAt
) {}
