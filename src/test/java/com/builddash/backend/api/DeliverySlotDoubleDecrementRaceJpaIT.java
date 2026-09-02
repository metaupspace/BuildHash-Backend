package com.builddash.backend.api;

import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.application.service.StaleOrderSweepService;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.builddash.backend.support.ApprovalTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.builddash.backend.support.ApprovalTestFixtures.seedCounter;
import static com.builddash.backend.support.ApprovalTestFixtures.seedUser;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * H2.4/H2.7 on real Postgres: one lock, three racers — release, consume, and the
 * expiry sweep — all entitled (under the old unconditional check-then-update code) to
 * decrement the same counter. The CAS must let exactly ONE transition win; the counter
 * moves by exactly one and the losers are no-ops. The non-expired variant pins the
 * status/count coherence: whichever of release/consume wins, the counter value matches
 * the terminal status.
 */
class DeliverySlotDoubleDecrementRaceJpaIT extends AbstractIntegrationTest {

    @Autowired
    private DeliverySlotService deliverySlotService;
    @Autowired
    private StaleOrderSweepService sweepService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void expiredLock_releaseVsConsumeVsExpirySweep_decrementsExactlyOnce() throws Exception {
        UUID userId = seedUser(jdbc);
        LocalDate date = LocalDate.now().plusDays(1);
        UUID slotId = seedCounter(jdbc, date, 10, 5);
        UUID lockId = ApprovalTestFixtures.seedActiveLock(jdbc, userId, slotId, date);
        jdbc.update("UPDATE delivery_slot_locks SET expires_at = now() - interval '5 minutes' WHERE id = ?", lockId);

        race(
                () -> deliverySlotService.releaseLock(lockId, userId),
                () -> deliverySlotService.consumeLock(lockId, userId),
                () -> sweepService.sweepExpiredLocks());

        // Exactly one CAS winner. RELEASED/EXPIRED return the capacity (5 -> 4); CONSUMED
        // keeps it held for a delivery that now owns the slot (5). The double-decrement
        // bug the CAS removes would show 3.
        String status = jdbc.queryForObject(
                "SELECT status FROM delivery_slot_locks WHERE id = ?", String.class, lockId);
        int count = ApprovalTestFixtures.counterCount(jdbc, slotId, date);
        assertThat(status).isIn("RELEASED", "EXPIRED", "CONSUMED");
        if ("CONSUMED".equals(status)) {
            assertThat(count).isEqualTo(5);
        } else {
            assertThat(count).isEqualTo(4);
        }
    }

    @Test
    void liveLock_releaseVsConsume_oneWins_counterMatchesStatus() throws Exception {
        UUID userId = seedUser(jdbc);
        LocalDate date = LocalDate.now().plusDays(1);
        UUID slotId = seedCounter(jdbc, date, 10, 5);
        UUID lockId = ApprovalTestFixtures.seedActiveLock(jdbc, userId, slotId, date);

        race(
                () -> deliverySlotService.releaseLock(lockId, userId),
                () -> deliverySlotService.consumeLock(lockId, userId));

        String status = jdbc.queryForObject(
                "SELECT status FROM delivery_slot_locks WHERE id = ?", String.class, lockId);
        int count = ApprovalTestFixtures.counterCount(jdbc, slotId, date);
        // RELEASED returns capacity (5 -> 4); CONSUMED keeps it held for delivery (5).
        // The incoherent outcomes — 3 (double decrement) or 4-with-CONSUMED — are the
        // exact bugs the CAS removes.
        if ("RELEASED".equals(status)) {
            assertThat(count).isEqualTo(4);
        } else {
            assertThat(status).isEqualTo("CONSUMED");
            assertThat(count).isEqualTo(5);
        }
    }

    /** Runs N callables as simultaneously as a latch allows; any failure fails the test. */
    private void race(Runnable... actions) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(actions.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.List<? extends Future<?>> futures = java.util.Arrays.stream(actions).map(a -> (Future<?>) pool.submit(() -> {
                await(start);
                a.run();
            })).toList();
            start.countDown();
            for (Future<?> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
