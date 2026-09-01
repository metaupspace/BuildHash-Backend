package com.builddash.backend.application.service;

/** Escalation sweep (9-D). Multi-instance safe via per-request REQUIRES_NEW + row
 *  lock + re-check; no ShedLock, no payment execution, never auto-approves or
 *  auto-cancels. */
public interface ApprovalEscalationService {

    /** @return number of requests escalated or blocked this pass */
    int escalateDue();
}
