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
@Table(name = "company_approval_policies")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalPolicyEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "company_id", nullable = false, unique = true)
    private UUID companyId;

    @Column(name = "amount_threshold", precision = 12, scale = 2)
    private BigDecimal amountThreshold;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "category_ids", columnDefinition = "uuid[]")
    private List<UUID> categoryIds;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "site_ids", columnDefinition = "uuid[]")
    private List<UUID> siteIds;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "role_stages", columnDefinition = "varchar[]", nullable = false)
    private List<String> roleStages;

    @Column(name = "escalation_hours", nullable = false)
    private int escalationHours;

    @Column(name = "version", nullable = false)
    private int version;

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
