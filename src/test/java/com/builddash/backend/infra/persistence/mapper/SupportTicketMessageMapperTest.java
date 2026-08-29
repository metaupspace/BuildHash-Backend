package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.enums.SupportTicketMessageSender;
import com.builddash.backend.domain.model.SupportTicketMessage;
import com.builddash.backend.infra.persistence.entity.SupportTicketMessageEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SupportTicketMessageMapperTest {

    @Test
    void roundTrip_preservesEveryField() {
        SupportTicketMessage message = new SupportTicketMessage(UUID.randomUUID(), UUID.randomUUID(),
                SupportTicketMessageSender.AGENT, "agent reply", Instant.now());

        SupportTicketMessage roundTripped = SupportTicketMessageMapper.toDomain(SupportTicketMessageMapper.toEntity(message));

        assertThat(roundTripped).isEqualTo(message);
    }
}
