package com.builddash.backend.application.scheduler;
import com.builddash.backend.application.service.ApplicationMetrics;

import com.builddash.backend.application.service.StatementEmailService;
import com.builddash.backend.application.service.StatementGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 9-E statement heartbeat: due-generation discovery, stuck-generation recovery, email
 * retry. Thin — all logic lives in the services. Multi-instance safety is entirely
 * database-side (company lock first, UNIQUE version constraint, conditional claim and
 * reclaim) — no ShedLock. "Closed months without READY" makes missed executions and
 * late startups self-healing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StatementScheduler {

    private final StatementGenerationService generationService;
    private final StatementEmailService emailService;
    private final ApplicationMetrics metrics;

    @Scheduled(fixedDelayString = "${statement.scheduler.delay-ms:300000}")
    public void sweep() {
        int generated = generationService.generateDue();
        int recovered = generationService.recoverStuck();
        int emailed = emailService.sweep();
        if (generated + recovered + emailed > 0) {
            log.info("Statement sweep: {} generated, {} recovered, {} emailed", generated, recovered, emailed);
        }
    }
}
