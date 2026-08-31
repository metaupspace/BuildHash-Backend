package com.builddash.backend.domain.enums;

public enum CartType {
    PRIMARY,
    REORDER_SCRATCH,
    /** B2B draft cart (RFQ/PO convert target, 9-B/9-C). Excluded from primary-cart lookups and abandonment sweeps by their type filters. */
    B2B_DRAFT
}
