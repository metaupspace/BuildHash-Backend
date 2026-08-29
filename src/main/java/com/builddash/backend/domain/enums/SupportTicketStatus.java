package com.builddash.backend.domain.enums;

/**
 * NAMED GAP — PERMANENT ESCALATED: Phase 7 exposes exactly one transition endpoint
 * (escalate), so every escalated ticket — including every chat-created ticket, since
 * /support/chat always escalates — stays ESCALATED forever: no resolve/close surface
 * exists yet. RESOLVED/CLOSED are locked enum values with no reachable transition in
 * this phase; a future agent/admin surface is the intended fix. Deliberate, visible,
 * not hidden behind "unreachable enum values".
 */
public enum SupportTicketStatus {
    OPEN,
    ESCALATED,
    RESOLVED,
    CLOSED
}
