package com.builddash.backend.application.service;

import com.builddash.backend.domain.enums.SupportTicketCategory;
import com.builddash.backend.domain.enums.SupportTicketMessageSender;
import com.builddash.backend.domain.model.SupportTicket;
import com.builddash.backend.domain.model.SupportTicketMessage;

import java.util.List;
import java.util.UUID;

/**
 * The support-ticket surface (PLAN_PHASE7 Sections 5(e)/8). Ownership model copies
 * ReturnService.getReturn: userId + roles travel together, non-owner non-privileged callers
 * get 404, VENDOR/ADMIN get elevated access by id.
 */
public interface SupportTicketService {

    SupportTicket createTicket(UUID userId, SupportTicketCategory category, String subject, String firstMessage);

    List<SupportTicket> listOwnTickets(UUID userId);

    SupportTicket getTicket(UUID userId, List<String> roles, UUID ticketId);

    List<SupportTicketMessage> listMessages(UUID userId, List<String> roles, UUID ticketId);

    SupportTicketMessage appendMessage(UUID userId, List<String> roles, UUID ticketId, String body);

    SupportTicket escalate(UUID ticketId);
}
