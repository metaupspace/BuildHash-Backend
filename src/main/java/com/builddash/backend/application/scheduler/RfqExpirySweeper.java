package com.builddash.backend.application.scheduler;

import com.builddash.backend.domain.port.RfqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Expires OPEN RFQs whose expiresAt has passed, with one conditional bulk
 * UPDATE per sweep (rfq.expiry.sweep-interval-ms, default 60000ms). Quote
 * submission and conversion serialize on the RFQ row lock; this sweeper relies
 * purely on the UPDATE's atomicity, so multiple scheduler instances are safe —
 * Postgres re-evaluates the status predicate against the locked row version,
 * making the state transition itself the source of truth.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RfqExpirySweeper {

    private final RfqRepository rfqRepository;

    @Scheduled(fixedDelayString = "${rfq.expiry.sweep-interval-ms:60000}")
    public int sweep() {
        int expired = rfqRepository.expireOpenBefore(Instant.now());
        if (expired > 0) {
            log.info("RFQ expiry sweep transitioned {} RFQ(s) OPEN -> EXPIRED", expired);
        }
        return expired;
    }
}
