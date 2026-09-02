package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.SupportTicket;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportTicketRepository {

    SupportTicket save(SupportTicket ticket);

    Optional<SupportTicket> findById(UUID id);

    List<SupportTicket> findByUserId(UUID userId);

    List<SupportTicket> findByUserId(UUID userId, int page, int size);

    /**
     * DPDP hard-delete (PLAN_PHASE8 5(d)) — gated behind account.deletion.support-tickets
     * (OQ-9: product/legal decision, HARD_DELETE is the executable default, RETAIN is a
     * one-config flip). Per-ticket, FK-ordered: messages die before their ticket.
     */
    void deleteById(UUID id);
}
