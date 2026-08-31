package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.model.PoImport;
import com.builddash.backend.domain.model.PoImportRow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Import resource with embedded row outcomes (locked decision: embedded). */
public record PoImportResponse(
        UUID id,
        UUID companyId,
        String idempotencyKey,
        String status,
        int totalRows,
        int validRows,
        int invalidRows,
        UUID draftCartId,
        Instant createdAt,
        List<PoImportRowResponse> rows
) {

    public static PoImportResponse from(PoImport poImport, List<PoImportRow> rows) {
        return new PoImportResponse(poImport.id(), poImport.companyId(),
                poImport.idempotencyKey(), poImport.status().name(), poImport.totalRows(),
                poImport.validRows(), poImport.invalidRows(), poImport.draftCartId(),
                poImport.createdAt(), rows.stream().map(PoImportRowResponse::from).toList());
    }
}
