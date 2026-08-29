package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.SupportTicket;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportTicketRepository {

    SupportTicket save(SupportTicket ticket);

    Optional<SupportTicket> findById(UUID id);

    List<SupportTicket> findByUserId(UUID userId);
}
