package com.builddash.backend.api.dto.response;

import com.builddash.backend.application.service.StatementQueryService.StatementView;
import com.builddash.backend.domain.model.Statement;
import com.builddash.backend.domain.model.StatementDiscrepancy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Statement API view (9-E) — signed URLs only, storage keys never leave the backend. */
public record StatementResponse(
        UUID id,
        UUID companyId,
        Instant periodStart,
        Instant periodEnd,
        String periodKey,
        String statementNumber,
        String status,
        int version,
        Integer orderCount,
        BigDecimal grossTotal,
        BigDecimal taxTotal,
        BigDecimal netTotal,
        BigDecimal creditTotal,
        BigDecimal dueTotal,
        String emailStatus,
        List<Discrepancy> discrepancies,
        Instant generatedAt,
        String pdfUrl,
        Instant pdfUrlExpiresAt,
        String xlsxUrl,
        Instant xlsxUrlExpiresAt
) {

    public static StatementResponse from(StatementView view) {
        Statement s = view.statement();
        return new StatementResponse(s.id(), s.companyId(), s.periodStart(), s.periodEnd(), s.periodKey(),
                s.statementNumber(), s.status().name(), s.version(), s.orderCount(),
                s.grossTotal(), s.taxTotal(), s.netTotal(), s.creditTotal(), s.dueTotal(),
                s.emailStatus().name(),
                s.discrepancies().stream().map(Discrepancy::from).toList(),
                s.generatedAt(), view.pdfUrl(), view.pdfUrlExpiresAt(), view.xlsxUrl(), view.xlsxUrlExpiresAt());
    }

    public record Discrepancy(String type, UUID orderId, String detail) {

        static Discrepancy from(StatementDiscrepancy d) {
            return new Discrepancy(d.type().name(), d.orderId(), d.detail());
        }
    }
}
