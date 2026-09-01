package com.builddash.backend.application.service;

/**
 * Statement generation (9-E) — invoice-pipeline shape: short claim transaction, all
 * rendering/storage outside transactions, short finalize transaction that allocates
 * the number only after both artifacts are stored. Failures are retryable
 * (PENDING/stale-GENERATING reclaim, attempt cap, DLQ_RETRY) — never a persisted
 * FAILED state, and never a silently inconsistent READY statement.
 */
public interface StatementGenerationService {

    /** Discovers closed months (per company timezone) lacking READY statements; empty
     *  months are skipped before any row or number exists. Bounded per pass. */
    int generateDue();

    /** Stale-GENERATING / DLQ_RETRY recovery, invoice scheduler shape. */
    int recoverStuck();

    /** Full pipeline for one claimed statement row. Package-visible recovery hook. */
    boolean process(java.util.UUID statementId);
}
