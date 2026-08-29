package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.SupportTicketMessageSender;

import java.time.Instant;
import java.util.UUID;

/** Append-only, ticket-scoped conversation entry. senderRole carries no user identity. */
public record SupportTicketMessage(
        UUID id,
        UUID ticketId,
        SupportTicketMessageSender senderRole,
        String body,
        Instant createdAt
) {
}
