package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.SupportTicketCategory;
import com.builddash.backend.domain.enums.SupportTicketStatus;
import com.builddash.backend.domain.exception.InvalidSupportTicketStateException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportTicketTransitionTest {

    private SupportTicket ticket(SupportTicketStatus status) {
        return new SupportTicket(UUID.randomUUID(), UUID.randomUUID(), SupportTicketCategory.ORDER_ISSUE,
                status, "subject", Instant.now().plusSeconds(3600), Instant.now(), Instant.now());
    }

    @Test
    void escalateFromOpen_succeeds() {
        SupportTicket escalated = ticket(SupportTicketStatus.OPEN).escalate();

        assertThat(escalated.status()).isEqualTo(SupportTicketStatus.ESCALATED);
        assertThat(escalated.id()).isEqualTo(escalated.id());
    }

    @Test
    void escalateFromAnythingElse_throws() {
        for (SupportTicketStatus status : SupportTicketStatus.values()) {
            if (status == SupportTicketStatus.OPEN) {
                continue;
            }
            assertThatThrownBy(() -> ticket(status).escalate())
                    .as("escalate from %s", status)
                    .isInstanceOf(InvalidSupportTicketStateException.class);
        }
    }
}
