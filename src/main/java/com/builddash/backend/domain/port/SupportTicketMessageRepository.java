package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.SupportTicketMessage;

import java.util.List;
import java.util.UUID;

public interface SupportTicketMessageRepository {

    SupportTicketMessage save(SupportTicketMessage message);

    List<SupportTicketMessage> findByTicketId(UUID ticketId);
}
