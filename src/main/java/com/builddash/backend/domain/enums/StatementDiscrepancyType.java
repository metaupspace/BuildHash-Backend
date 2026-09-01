package com.builddash.backend.domain.enums;

/** Invoice-readiness discrepancies recorded on a statement (9-E). The order stays in
 *  the totals — invoices carry no monetary values, so nothing is imputed or omitted. */
public enum StatementDiscrepancyType {
    INVOICE_MISSING,
    INVOICE_NOT_READY
}
