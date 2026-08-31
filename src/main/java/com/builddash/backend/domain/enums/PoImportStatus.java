package com.builddash.backend.domain.enums;

/**
 * RECEIVED/PARSED are transient in-request states of the upload pipeline; the
 * persisted end states are REVIEW (parse succeeded, rows recorded, awaiting
 * conversion), CONVERTED (draft cart produced) and FAILED_STRUCTURE (whole-file
 * rejection — the company + Idempotency-Key pair stays consumed).
 */
public enum PoImportStatus {
    RECEIVED,
    PARSED,
    REVIEW,
    CONVERTED,
    FAILED_STRUCTURE
}
