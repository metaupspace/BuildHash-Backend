package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.ModerationStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class Review {

    private UUID id;
    private UUID productId;
    private UUID userId;
    private int rating;
    private String comment;
    private ModerationStatus status = ModerationStatus.APPROVED;

    /**
     * Stubbed for Phase 1 Wave 2 — no Order module exists yet to join against, so this is
     * always false. See PROGRESS.md for the deferral decision.
     */
    private boolean verifiedPurchase;

    private Instant createdAt;
}
