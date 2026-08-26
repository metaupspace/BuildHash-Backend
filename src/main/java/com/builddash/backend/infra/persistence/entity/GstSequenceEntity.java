package com.builddash.backend.infra.persistence.entity;

import com.builddash.backend.domain.enums.GstSequenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "gst_sequences")
@Getter
@Setter
@NoArgsConstructor
public class GstSequenceEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "sequence_type")
    private GstSequenceType sequenceType;

    @Column(name = "fiscal_year", nullable = false)
    private String fiscalYear;

    @Column(nullable = false)
    private String prefix;

    @Column(name = "current_val", nullable = false)
    private long currentVal;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
