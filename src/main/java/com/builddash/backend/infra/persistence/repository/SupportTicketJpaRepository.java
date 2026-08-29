package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.SupportTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportTicketJpaRepository extends JpaRepository<SupportTicketEntity, UUID> {

    List<SupportTicketEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
