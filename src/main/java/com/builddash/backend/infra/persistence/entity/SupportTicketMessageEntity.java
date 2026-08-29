package com.builddash.backend.infra.persistence.entity;

import com.builddash.backend.domain.enums.SupportTicketMessageSender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "support_ticket_messages")
@Getter
@Setter
@NoArgsConstructor
public class SupportTicketMessageEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "ticket_id")
    private UUID ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role")
    private SupportTicketMessageSender senderRole;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
