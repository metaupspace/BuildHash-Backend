package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.GstNoteType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GstNote(
        UUID id,
        UUID returnId,
        GstNoteType noteType,
        String number,
        BigDecimal amount,
        Instant generatedAt,
        Instant createdAt,
        Instant updatedAt
) {}
