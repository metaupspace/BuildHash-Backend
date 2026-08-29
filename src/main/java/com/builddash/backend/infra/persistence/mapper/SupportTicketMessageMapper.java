package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.SupportTicketMessage;
import com.builddash.backend.infra.persistence.entity.SupportTicketMessageEntity;

public final class SupportTicketMessageMapper {

    private SupportTicketMessageMapper() {
    }

    public static SupportTicketMessage toDomain(SupportTicketMessageEntity entity) {
        return new SupportTicketMessage(entity.getId(), entity.getTicketId(), entity.getSenderRole(),
                entity.getBody(), entity.getCreatedAt());
    }

    public static SupportTicketMessageEntity toEntity(SupportTicketMessage message) {
        SupportTicketMessageEntity entity = new SupportTicketMessageEntity();
        entity.setId(message.id());
        entity.setTicketId(message.ticketId());
        entity.setSenderRole(message.senderRole());
        entity.setBody(message.body());
        entity.setCreatedAt(message.createdAt());
        return entity;
    }
}
