package com.builddash.backend.application.scheduler;

import com.builddash.backend.application.service.ApprovalEscalationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 9-D escalation heartbeat. 60s default; multi-instance safe by construction (per-request
 * REQUIRES_NEW + row lock + re-check in ApprovalEscalationServiceImpl) — no ShedLock.
 * Never executes payment.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalEscalationJob {

    private final ApprovalEscalationService escalationService;

    @Scheduled(fixedDelayString = "${approval.escalation.sweep-interval-ms:60000}")
    public void escalateDueApprovals() {
        int processed = escalationService.escalateDue();
        if (processed > 0) {
            log.info("Approval escalation pass processed {} request(s)", processed);
        }
    }
}
