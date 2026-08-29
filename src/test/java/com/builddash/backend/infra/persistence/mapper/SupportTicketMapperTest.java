package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.enums.SupportTicketCategory;
import com.builddash.backend.domain.enums.SupportTicketStatus;
import com.builddash.backend.domain.model.SupportTicket;
import com.builddash.backend.infra.persistence.entity.SupportTicketEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SupportTicketMapperTest {

    @Test
    void roundTrip_preservesEveryField() {
        SupportTicket ticket = new SupportTicket(UUID.randomUUID(), UUID.randomUUID(),
                SupportTicketCategory.RETURN_REFUND_ISSUE, SupportTicketStatus.ESCALATED,
                "subject", Instant.now(), Instant.now(), Instant.now());

        SupportTicket roundTripped = SupportTicketMapper.toDomain(SupportTicketMapper.toEntity(ticket));

        assertThat(roundTripped).isEqualTo(ticket);
    }
}
