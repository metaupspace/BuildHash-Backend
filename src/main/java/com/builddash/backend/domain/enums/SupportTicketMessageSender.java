package com.builddash.backend.domain.enums;

/**
 * Derived from the caller's role claim at write time — the message model carries no user
 * id, so a customer sees "an agent replied", never which agent (PLAN_PHASE7 Section 4).
 */
public enum SupportTicketMessageSender {
    CUSTOMER,
    AGENT
}
