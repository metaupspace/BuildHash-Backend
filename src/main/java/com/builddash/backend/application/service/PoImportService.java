package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.PoImport;
import com.builddash.backend.domain.model.PoImportRow;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * B2B bulk PO import (PO_UPLOAD): streaming XLSX parse, per-row validation,
 * draft-cart creation from valid rows. (companyId, Idempotency-Key) identifies
 * the import — a replay or race loser resolves to the existing resource,
 * including FAILED_STRUCTURE (structural failure consumes the key).
 */
public interface PoImportService {

    ImportResult importWorkbook(UUID userId, UUID companyId, String idempotencyKey, MultipartFile file);

    /** PO_VIEW; company scoped — non-members get 404. */
    ImportDetail get(UUID userId, UUID importId);

    record ImportResult(PoImport poImport, List<PoImportRow> rows, boolean replay) {
    }

    record ImportDetail(PoImport poImport, List<PoImportRow> rows) {
    }
}
