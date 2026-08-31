package com.builddash.backend.application.scheduler;

import com.builddash.backend.domain.port.IdempotencyKeyRepository;
import com.builddash.backend.infra.config.OrderIdempotencyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Nightly idempotency-key hygiene (PLAN_PHASE8 decision 10): deletes keys older than the
 * configured window. Table-hygiene only — correctness is the read-path filter
 * (IdempotencyKeyRepositoryAdapter.findOrderId), so a missed purge run degrades nothing.
 * CatalogOutboxRelay discipline: one bulk delete, log the count.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyPurgeScheduler {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OrderIdempotencyProperties properties;

    @Scheduled(cron = "${orders.idempotency-purge-cron:0 15 4 * * *}")
    public void purge() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(properties.getIdempotencyWindowHours()));
        int removed = idempotencyKeyRepository.deleteCreatedBefore(cutoff);
        if (removed > 0) {
            log.info("Idempotency purge: removed {} keys older than {}", removed, cutoff);
        }
    }
}
