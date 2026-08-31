package com.builddash.backend.domain.enums;

/**
 * 9-B scope: quotes are SUBMITTED only — no withdrawal/revision/acceptance/
 * rejection lifecycle (locked OQ-5). Selection-worthiness is derived at read
 * time from validUntil, never stored.
 */
public enum RfqQuoteStatus {
    SUBMITTED
}
