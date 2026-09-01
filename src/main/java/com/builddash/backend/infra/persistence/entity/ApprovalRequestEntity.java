package com.builddash.backend.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Caller-assigned id (domain supplies UUID.randomUUID()) — no @UuidGenerator so a
 *  merge can never regenerate identity and break foreign keys (9-C lesson). */
@Entity
@Table(name = "approval_requests")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalRequestEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "current_stage_index", nullable = false)
    private int currentStageIndex;

    @Column(name = "current_stage_role", nullable = false)
    private String currentRole;

    @Column(name = "assigned_member_id")
    private UUID assignedMemberId;

    @Column(name = "escalation_due_at")
    private Instant escalationDueAt;

    @Column(name = "order_total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal orderTotalAmount;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "matched_rules", columnDefinition = "varchar[]")
    private List<String> matchedRules;

    @Column(name = "threshold_amount", precision = 12, scale = 2)
    private BigDecimal thresholdAmount;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "matched_category_ids", columnDefinition = "uuid[]")
    private List<UUID> matchedCategoryIds;

    @Column(name = "site_id")
    private UUID siteId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "role_stages", columnDefinition = "varchar[]", nullable = false)
    private List<String> roleStages;

    @Column(name = "escalation_hours", nullable = false)
    private int escalationHours;

    @Column(name = "policy_version", nullable = false)
    private int policyVersion;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
