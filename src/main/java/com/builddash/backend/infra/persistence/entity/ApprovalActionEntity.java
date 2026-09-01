package com.builddash.backend.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Append-only. Caller-assigned id (domain supplies UUID.randomUUID()) — no
 *  @UuidGenerator so a merge can never regenerate identity (9-C lesson). */
@Entity
@Table(name = "approval_actions")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalActionEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "actor_member_id")
    private UUID actorMemberId;

    @Column(name = "delegate_member_id")
    private UUID delegateMemberId;

    @Column(name = "stage_index", nullable = false)
    private int stageIndex;

    @Column(name = "detail")
    private String detail;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
