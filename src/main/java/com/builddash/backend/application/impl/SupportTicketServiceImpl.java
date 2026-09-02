package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.SupportTicketService;
import com.builddash.backend.domain.enums.SupportTicketCategory;
import com.builddash.backend.domain.enums.SupportTicketMessageSender;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.SupportTicket;
import com.builddash.backend.domain.model.SupportTicketMessage;
import com.builddash.backend.domain.port.SupportTicketMessageRepository;
import com.builddash.backend.domain.port.SupportTicketRepository;
import com.builddash.backend.infra.config.SupportProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportTicketServiceImpl implements SupportTicketService {

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketMessageRepository messageRepository;
    private final SupportProperties supportProperties;

    @Override
    @Transactional
    public SupportTicket createTicket(UUID userId, SupportTicketCategory category, String subject, String firstMessage) {
        Instant now = Instant.now();
        SupportTicket ticket = new SupportTicket(UUID.randomUUID(), userId, category,
                com.builddash.backend.domain.enums.SupportTicketStatus.OPEN, subject,
                now.plus(supportProperties.slaFor(category)), now, now);
        SupportTicket saved = ticketRepository.save(ticket);

        messageRepository.save(new SupportTicketMessage(UUID.randomUUID(), saved.id(),
                SupportTicketMessageSender.CUSTOMER, firstMessage, Instant.now()));
        return saved;
    }

    @Override
    public List<SupportTicket> listOwnTickets(UUID userId) {
        return listOwnTickets(userId, 0, 20);
    }

    @Override
    public List<SupportTicket> listOwnTickets(UUID userId, int page, int size) {
        return ticketRepository.findByUserId(userId, page, size);
    }

    @Override
    public SupportTicket getTicket(UUID userId, List<String> roles, UUID ticketId) {
        return fetchAuthorized(userId, roles, ticketId);
    }

    @Override
    public List<SupportTicketMessage> listMessages(UUID userId, List<String> roles, UUID ticketId) {
        // Parent authorization FIRST — messages carry no userId, so their access control
        // is entirely the ticket's. Nothing is read before the caller clears the gate.
        fetchAuthorized(userId, roles, ticketId);
        return messageRepository.findByTicketId(ticketId);
    }

    @Override
    @Transactional
    public SupportTicketMessage appendMessage(UUID userId, List<String> roles, UUID ticketId, String body) {
        // Parent authorization BEFORE the write: a non-owner's append is rejected before
        // any message row exists — not written-then-hidden.
        fetchAuthorized(userId, roles, ticketId);
        SupportTicketMessageSender sender = isPrivileged(roles)
                ? SupportTicketMessageSender.AGENT
                : SupportTicketMessageSender.CUSTOMER;
        return messageRepository.save(new SupportTicketMessage(UUID.randomUUID(), ticketId, sender, body, Instant.now()));
    }

    @Override
    @Transactional
    public SupportTicket escalate(UUID ticketId) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("SUPPORT_TICKET_NOT_FOUND", "Support ticket not found: " + ticketId));
        SupportTicket escalated = ticket.escalate();
        return ticketRepository.save(escalated);
    }

    private SupportTicket fetchAuthorized(UUID userId, List<String> roles, UUID ticketId) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("SUPPORT_TICKET_NOT_FOUND", "Support ticket not found: " + ticketId));
        if (!isPrivileged(roles) && !ticket.userId().equals(userId)) {
            // House 404-for-non-owner convention: no existence leak to unrelated callers.
            throw new NotFoundException("SUPPORT_TICKET_NOT_FOUND", "Support ticket not found: " + ticketId);
        }
        return ticket;
    }

    private boolean isPrivileged(List<String> roles) {
        return roles != null && (roles.contains("ADMIN") || roles.contains("VENDOR"));
    }
}
