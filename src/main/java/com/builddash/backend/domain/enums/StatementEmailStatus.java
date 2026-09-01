package com.builddash.backend.domain.enums;

/** Email delivery state (9-E), tracked separately from generation: READY never
 *  depends on delivery. SKIPPED = company has no statementEmail configured. */
public enum StatementEmailStatus {
    NONE,
    SENT,
    FAILED,
    SKIPPED
}
