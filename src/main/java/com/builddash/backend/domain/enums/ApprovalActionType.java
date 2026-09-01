package com.builddash.backend.domain.enums;

/** Append-only approval audit trail entry types (9-D). ESCALATION_BLOCKED is written
 *  at most once per stage — the DB backstop UNIQUE(request_id, action_type, stage_index)
 *  enforces it even across racing scheduler instances. */
public enum ApprovalActionType {
    APPROVED,
    REJECTED,
    DELEGATED,
    ESCALATED,
    ESCALATION_BLOCKED,
    CANCELLED
}
