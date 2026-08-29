package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.SupportTicketMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportTicketMessageJpaRepository extends JpaRepository<SupportTicketMessageEntity, UUID> {

    List<SupportTicketMessageEntity> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
