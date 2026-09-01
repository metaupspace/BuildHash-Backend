package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.Statement;

import java.util.List;
import java.util.UUID;

/** Statement reads (9-E): STATEMENT_VIEW company-wide, signed URLs, no storage keys. */
public interface StatementQueryService {

    /** Latest version per period, newest period first. */
    List<StatementView> list(UUID userId, UUID companyId);

    /** Requested version by id — historical READY versions stay accessible. */
    StatementView get(UUID userId, UUID statementId);

    record StatementView(Statement statement, String pdfUrl, java.time.Instant pdfUrlExpiresAt,
                         String xlsxUrl, java.time.Instant xlsxUrlExpiresAt) {
    }
}
