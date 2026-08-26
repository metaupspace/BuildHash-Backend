package com.builddash.backend.infra.persistence.entity;

import com.builddash.backend.domain.enums.GstNoteType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gst_notes")
@Getter
@Setter
@NoArgsConstructor
public class GstNoteEntity {

    @Id
    private UUID id;

    @Column(name = "return_id", nullable = false)
    private UUID returnId;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false)
    private GstNoteType noteType;

    @Column(nullable = false, unique = true)
    private String number;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
