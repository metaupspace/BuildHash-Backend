package com.builddash.backend.domain.model;

/**
 * Company header shown on statement documents (9-E). Shared by the PDF renderer and the
 * XLSX writer — a projection of {@link Company}, not the aggregate itself.
 */
public record StatementCompanyInfo(String name, String gstNumber, String statementEmail) {
}
