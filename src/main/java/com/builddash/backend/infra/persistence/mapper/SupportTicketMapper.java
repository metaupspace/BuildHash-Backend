package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.SupportTicket;
import com.builddash.backend.infra.persistence.entity.SupportTicketEntity;

public final class SupportTicketMapper {

    private SupportTicketMapper() {
    }

    public static SupportTicket toDomain(SupportTicketEntity entity) {
        return new SupportTicket(entity.getId(), entity.getUserId(), entity.getCategory(), entity.getStatus(),
                entity.getSubject(), entity.getSlaDueAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public static SupportTicketEntity toEntity(SupportTicket ticket) {
        SupportTicketEntity entity = new SupportTicketEntity();
        entity.setId(ticket.id());
        entity.setUserId(ticket.userId());
        entity.setCategory(ticket.category());
        entity.setStatus(ticket.status());
        entity.setSubject(ticket.subject());
        entity.setSlaDueAt(ticket.slaDueAt());
        entity.setCreatedAt(ticket.createdAt());
        entity.setUpdatedAt(ticket.updatedAt());
        return entity;
    }
}
