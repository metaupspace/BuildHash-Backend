package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.SupportTicketCategory;
import com.builddash.backend.domain.enums.SupportTicketStatus;
import com.builddash.backend.domain.exception.InvalidSupportTicketStateException;

import java.time.Instant;
import java.util.UUID;

/**
 * slaDueAt is computed once at creation from the category's configured SLA and stored —
 * queryable, no breach-notification job (OQ-6b). The only transition Phase 7 exposes is
 * escalate(); see SupportTicketStatus for the named permanent-ESCALATED gap.
 */
public record SupportTicket(
        UUID id,
        UUID userId,
        SupportTicketCategory category,
        SupportTicketStatus status,
        String subject,
        Instant slaDueAt,
        Instant createdAt,
        Instant updatedAt
) {

    public SupportTicket escalate() {
        if (status != SupportTicketStatus.OPEN) {
            throw new InvalidSupportTicketStateException(status.name(), SupportTicketStatus.ESCALATED.name());
        }
        return new SupportTicket(id, userId, category, SupportTicketStatus.ESCALATED, subject,
                slaDueAt, createdAt, Instant.now());
    }
}
