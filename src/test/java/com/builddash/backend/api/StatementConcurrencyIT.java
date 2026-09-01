package com.builddash.backend.api;

import com.builddash.backend.application.service.StatementGenerationService;
import com.builddash.backend.domain.enums.StatementStatus;
import com.builddash.backend.domain.model.Statement;
import com.builddash.backend.domain.port.StatementRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.builddash.backend.support.StatementTestFixtures.seedCompany;
import static com.builddash.backend.support.StatementTestFixtures.seedConfirmedOrder;
import static com.builddash.backend.support.StatementTestFixtures.seedUser;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 9-E concurrency on real Postgres: two scheduler instances racing the same
 * company/period produce exactly one generation; stale-generation reclaim; the
 * UNIQUE(company, period, version) backstop; and per-company number allocation never
 * duplicates within a company.
 */
class StatementConcurrencyIT extends AbstractIntegrationTest {

    private static final Instant AUGUST = Instant.parse("2026-08-15T10:00:00Z");

    @Autowired
    private StatementGenerationService generationService;
    @Autowired
    private StatementRepository statementRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID companyWithAugustOrder(String name) {
        UUID companyId = seedCompany(jdbc, name, "Asia/Kolkata", "a@b.c");
        UUID user = seedUser(jdbc);
        seedConfirmedOrder(jdbc, companyId, user, null, AUGUST, "118.00", "18.00");
        return companyId;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Test
    void twoSchedulerInstances_oneGeneration() throws Exception {
        UUID companyId = companyWithAugustOrder("RaceCo");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger totalStarted = new AtomicInteger();
        try {
            List<Future<Integer>> futures = List.of(
                    pool.submit(() -> { await(start); return generationService.generateDue(); }),
                    pool.submit(() -> { await(start); return generationService.generateDue(); }));
            start.countDown();
            for (Future<Integer> f : futures) {
                totalStarted.addAndGet(f.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        // Exactly one instance claimed the period; the other saw the fresh GENERATING/
        // READY row and skipped. One row, one number, READY.
        assertThat(totalStarted.get()).isEqualTo(1);
        List<Statement> rows = statementRepository.findByCompanyIdOrderByPeriodStartDescVersionDesc(companyId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).status()).isEqualTo(StatementStatus.READY);
        assertThat(rows.get(0).statementNumber()).isNotNull();
        Integer numbers = jdbc.queryForObject(
                "SELECT count(DISTINCT statement_number) FROM statements WHERE company_id = ?",
                Integer.class, companyId);
        assertThat(numbers).isEqualTo(1);
    }

    @Test
    void staleGeneration_reclaimedByRecovery() {
        UUID companyId = companyWithAugustOrder("StaleCo");
        generationService.generateDue();
        Statement ready = statementRepository
                .findByCompanyIdOrderByPeriodStartDescVersionDesc(companyId).get(0);
        assertThat(ready.status()).isEqualTo(StatementStatus.READY);

        // Simulate a crashed regeneration: a GENERATING row gone stale, attempt under cap.
        jdbc.update("UPDATE statements SET status = 'GENERATING', attempt_count = 1, "
                + "updated_at = now() - interval '20 minutes' WHERE company_id = ?", companyId);

        int recovered = generationService.recoverStuck();
        assertThat(recovered).isGreaterThanOrEqualTo(1);
        List<Statement> rows = statementRepository
                .findByCompanyIdOrderByPeriodStartDescVersionDesc(companyId);
        assertThat(rows.get(0).status()).isEqualTo(StatementStatus.READY);
    }

    @Test
    void concurrentNumberAllocation_perCompanyCountersDistinctNumbers() throws Exception {
        // Two companies, same period, generated concurrently: each allocates from its
        // own (company, period) counter — same readable number, never a duplicate
        // within a company.
        UUID companyA = companyWithAugustOrder("NumberCoA");
        UUID companyB = companyWithAugustOrder("NumberCoB");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = List.of(
                    pool.submit(() -> { await(start); return generationService.generateDue(); }),
                    pool.submit(() -> { await(start); return generationService.generateDue(); }));
            start.countDown();
            for (Future<Integer> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        for (UUID companyId : List.of(companyA, companyB)) {
            List<Statement> rows = statementRepository
                    .findByCompanyIdOrderByPeriodStartDescVersionDesc(companyId);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).statementNumber()).isEqualTo("ST-202608-0001");
        }
        // No cross-company sequence interference: both counters at 1.
        Integer a = jdbc.queryForObject(
                "SELECT current_val FROM statement_sequences WHERE company_id = ? AND period_key = '202608'",
                Long.class, companyA).intValue();
        Integer b = jdbc.queryForObject(
                "SELECT current_val FROM statement_sequences WHERE company_id = ? AND period_key = '202608'",
                Long.class, companyB).intValue();
        assertThat(a).isEqualTo(1);
        assertThat(b).isEqualTo(1);
    }

    @Test
    void uniqueBackstop_directVersionInsertCollisionsRejected() {
        UUID companyId = companyWithAugustOrder("UniqueCo");
        generationService.generateDue();

        // A second v1 row for the same period must be rejected by the constraint —
        // translated after the failed transaction, exactly like the service does.
        UUID existing = statementRepository
                .findByCompanyIdOrderByPeriodStartDescVersionDesc(companyId).get(0).id();
        var result = jdbc.queryForObject(
                "SELECT period_start, period_end FROM statements WHERE id = ?",
                (rs, i) -> new Instant[]{rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant()},
                existing);
        boolean collided = false;
        try {
            jdbc.update("INSERT INTO statements (id, company_id, period_start, period_end, period_key, "
                            + "status, version, email_status, attempt_count, email_attempt_count) "
                            + "VALUES (?, ?, ?, ?, '202608', 'PENDING', 1, 'NONE', 0, 0)",
                    UUID.randomUUID(), companyId,
                    java.sql.Timestamp.from(result[0]), java.sql.Timestamp.from(result[1]));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            collided = true;
        }
        assertThat(collided).isTrue();
    }
}
