package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.PoImportStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One bulk XLSX upload. (companyId, idempotencyKey) is unique — a replay or a
 * race loser resolves to the existing resource, including FAILED_STRUCTURE
 * (structural failure consumes the key; a corrected file needs a new one).
 * Site selection is deferred from 9-C: imports and conversion are company-scoped.
 */
public record PoImport(
        UUID id,
        UUID companyId,
        String idempotencyKey,
        UUID uploadedBy,
        PoImportStatus status,
        int totalRows,
        int validRows,
        int invalidRows,
        UUID draftCartId,
        Instant createdAt,
        Instant updatedAt
) {

    public boolean review() {
        return status == PoImportStatus.REVIEW;
    }

    public PoImport withStatus(PoImportStatus newStatus) {
        return new PoImport(id, companyId, idempotencyKey, uploadedBy, newStatus,
                totalRows, validRows, invalidRows, draftCartId, createdAt, updatedAt);
    }

    public PoImport converted(UUID cartId) {
        return new PoImport(id, companyId, idempotencyKey, uploadedBy, PoImportStatus.CONVERTED,
                totalRows, validRows, invalidRows, cartId, createdAt, updatedAt);
    }
}
