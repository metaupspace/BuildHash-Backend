package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.StatementEmailStatus;
import com.builddash.backend.domain.enums.StatementStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One monthly statement generation for a company period (9-E). Lifecycle mirrors
 * Invoice: PENDING/GENERATING/READY/DLQ_RETRY with attempt-based recovery; READY rows
 * are immutable — regeneration is a new row with a new version and a new number.
 * Totals are the persisted order-line aggregates; credits come from GST credit notes
 * in the same period. pdf/xlsx_size_bytes let the email sweep reject oversized sends
 * before loading artifact bytes.
 */
public record Statement(
        UUID id,
        UUID companyId,
        Instant periodStart,
        Instant periodEnd,
        String periodKey,
        StatementStatus status,
        int version,
        String statementNumber,
        String pdfStorageKey,
        String xlsxStorageKey,
        Long pdfSizeBytes,
        Long xlsxSizeBytes,
        Instant generatedAt,
        int attemptCount,
        StatementEmailStatus emailStatus,
        Instant emailedAt,
        int emailAttemptCount,
        Integer orderCount,
        BigDecimal grossTotal,
        BigDecimal taxTotal,
        BigDecimal netTotal,
        BigDecimal creditTotal,
        BigDecimal dueTotal,
        List<StatementDiscrepancy> discrepancies,
        Instant createdAt,
        Instant updatedAt
) {

    public Statement {
        discrepancies = discrepancies == null ? List.of() : List.copyOf(discrepancies);
    }

    public Statement claim() {
        return with(StatementStatus.GENERATING, attemptCount + 1, null);
    }

    public Statement markReady(String number, String pdfKey, String xlsxKey, long pdfBytes, long xlsxBytes,
                               int orderCount, BigDecimal gross, BigDecimal tax, BigDecimal net,
                               BigDecimal credit, BigDecimal due, List<StatementDiscrepancy> discrepancies) {
        return new Statement(id, companyId, periodStart, periodEnd, periodKey, StatementStatus.READY, version,
                number, pdfKey, xlsxKey, pdfBytes, xlsxBytes, Instant.now(), attemptCount,
                emailStatus, emailedAt, emailAttemptCount, orderCount, gross, tax, net, credit, due,
                discrepancies, createdAt, Instant.now());
    }

    public Statement markDlqRetry() {
        return with(StatementStatus.DLQ_RETRY, attemptCount, null);
    }

    public Statement markPending() {
        return with(StatementStatus.PENDING, attemptCount, null);
    }

    public Statement markEmailSent() {
        return new Statement(id, companyId, periodStart, periodEnd, periodKey, status, version,
                statementNumber, pdfStorageKey, xlsxStorageKey, pdfSizeBytes, xlsxSizeBytes, generatedAt,
                attemptCount, StatementEmailStatus.SENT, Instant.now(), emailAttemptCount + 1,
                orderCount, grossTotal, taxTotal, netTotal, creditTotal, dueTotal, discrepancies,
                createdAt, Instant.now());
    }

    public Statement markEmailFailed() {
        return new Statement(id, companyId, periodStart, periodEnd, periodKey, status, version,
                statementNumber, pdfStorageKey, xlsxStorageKey, pdfSizeBytes, xlsxSizeBytes, generatedAt,
                attemptCount, StatementEmailStatus.FAILED, emailedAt, emailAttemptCount + 1,
                orderCount, grossTotal, taxTotal, netTotal, creditTotal, dueTotal, discrepancies,
                createdAt, Instant.now());
    }

    public Statement markEmailSkipped() {
        return new Statement(id, companyId, periodStart, periodEnd, periodKey, status, version,
                statementNumber, pdfStorageKey, xlsxStorageKey, pdfSizeBytes, xlsxSizeBytes, generatedAt,
                attemptCount, StatementEmailStatus.SKIPPED, emailedAt, emailAttemptCount,
                orderCount, grossTotal, taxTotal, netTotal, creditTotal, dueTotal, discrepancies,
                createdAt, Instant.now());
    }

    public boolean isReady() {
        return status == StatementStatus.READY;
    }

    private Statement with(StatementStatus next, int attempts, Instant generatedAt) {
        return new Statement(id, companyId, periodStart, periodEnd, periodKey, next, version,
                statementNumber, pdfStorageKey, xlsxStorageKey, pdfSizeBytes, xlsxSizeBytes, generatedAt,
                attempts, emailStatus, emailedAt, emailAttemptCount, orderCount, grossTotal, taxTotal,
                netTotal, creditTotal, dueTotal, discrepancies, createdAt, Instant.now());
    }
}
