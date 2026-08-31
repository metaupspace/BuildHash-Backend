package com.builddash.backend.domain.enums;

/**
 * Only OPEN admits quote submission, cancellation and conversion; EXPIRED,
 * CONVERTED and CANCELLED are terminal. Transitions are guarded under the RFQ
 * row lock (submission/cancel/convert) or the sweeper's conditional UPDATE.
 */
public enum RfqStatus {
    OPEN,
    EXPIRED,
    CONVERTED,
    CANCELLED
}
