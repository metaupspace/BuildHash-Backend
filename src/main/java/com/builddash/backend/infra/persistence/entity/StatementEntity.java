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
@Table(name = "statements")
@Getter
@Setter
@NoArgsConstructor
public class StatementEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "period_key", nullable = false)
    private String periodKey;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "statement_number")
    private String statementNumber;

    @Column(name = "pdf_storage_key")
    private String pdfStorageKey;

    @Column(name = "xlsx_storage_key")
    private String xlsxStorageKey;

    @Column(name = "pdf_size_bytes")
    private Long pdfSizeBytes;

    @Column(name = "xlsx_size_bytes")
    private Long xlsxSizeBytes;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "email_status", nullable = false)
    private String emailStatus;

    @Column(name = "emailed_at")
    private Instant emailedAt;

    @Column(name = "email_attempt_count", nullable = false)
    private int emailAttemptCount;

    @Column(name = "order_count")
    private Integer orderCount;

    @Column(name = "gross_total", precision = 12, scale = 2)
    private BigDecimal grossTotal;

    @Column(name = "tax_total", precision = 12, scale = 2)
    private BigDecimal taxTotal;

    @Column(name = "net_total", precision = 12, scale = 2)
    private BigDecimal netTotal;

    @Column(name = "credit_total", precision = 12, scale = 2)
    private BigDecimal creditTotal;

    @Column(name = "due_total", precision = 12, scale = 2)
    private BigDecimal dueTotal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "discrepancies", columnDefinition = "jsonb")
    private List<String> discrepanciesJson;

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
