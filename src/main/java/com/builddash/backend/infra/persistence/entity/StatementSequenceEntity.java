package com.builddash.backend.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Per-company per-period statement counter (9-E) — separate from gst_sequences. */
@Entity
@Table(name = "statement_sequences")
@Getter
@Setter
@NoArgsConstructor
@IdClass(StatementSequenceEntity.SequenceId.class)
public class StatementSequenceEntity {

    @Id
    @Column(name = "company_id")
    private UUID companyId;

    @Id
    @Column(name = "period_key")
    private String periodKey;

    @Column(name = "current_val", nullable = false)
    private long currentVal;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public StatementSequenceEntity(UUID companyId, String periodKey) {
        this.companyId = companyId;
        this.periodKey = periodKey;
        this.currentVal = 0;
        this.updatedAt = Instant.now();
    }

    @jakarta.persistence.PrePersist
    @jakarta.persistence.PreUpdate
    void stampTimestamps() {
        updatedAt = Instant.now();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SequenceId implements java.io.Serializable {
        private UUID companyId;
        private String periodKey;
    }
}
