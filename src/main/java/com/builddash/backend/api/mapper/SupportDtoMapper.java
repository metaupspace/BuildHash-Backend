package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.ChatResponse;
import com.builddash.backend.api.dto.response.SupportTicketMessageResponse;
import com.builddash.backend.api.dto.response.SupportTicketResponse;
import com.builddash.backend.application.service.SupportChatService;
import com.builddash.backend.domain.model.SupportTicket;
import com.builddash.backend.domain.model.SupportTicketMessage;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SupportDtoMapper {

    public SupportTicketResponse toResponse(SupportTicket ticket) {
        return new SupportTicketResponse(ticket.id(), ticket.category(), ticket.status(), ticket.subject(),
                ticket.slaDueAt(), ticket.createdAt(), ticket.updatedAt());
    }

    public SupportTicketMessageResponse toResponse(SupportTicketMessage message) {
        return new SupportTicketMessageResponse(message.id(), message.senderRole(), message.body(), message.createdAt());
    }

    public ChatResponse toResponse(SupportChatService.ChatOutcome outcome) {
        return new ChatResponse(outcome.intent(), outcome.confidence(), outcome.escalated(),
                outcome.ticket() != null ? outcome.ticket().id() : null);
    }
}
