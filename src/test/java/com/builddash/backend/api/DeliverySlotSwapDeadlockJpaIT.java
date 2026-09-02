package com.builddash.backend.api;

import com.builddash.backend.application.service.DeliverySlotService;
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
 * H2.5 on real Postgres: two swaps in OPPOSITE directions must not deadlock. Before the
 * canonical (slotId, then slotDate) counter lock order, user1's A->B swap locked A then
 * wanted B while user2's B->A swap locked B then wanted A — a textbook AB/BA cycle that
 * Postgres only resolves by killing a victim (a 500 for one user). Each iteration races
 * fresh rows; the loop gives the bad interleaving many chances to bite. The counter
 * assertions also pin the accounting: two opposing swaps are capacity-neutral for both
 * slots. Covers both swap paths: acquireOrSwapLock (ACTIVE) and swapConsumedLock
 * (CONSUMED, the reschedule path).
 */
class DeliverySlotSwapDeadlockJpaIT extends AbstractIntegrationTest {

    private static final int ITERATIONS = 15;

    @Autowired
    private DeliverySlotService deliverySlotService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void opposingAcquireOrSwapLocks_neverDeadlock_capacityNeutral() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);
        for (int i = 0; i < ITERATIONS; i++) {
            UUID user1 = seedUser(jdbc);
            UUID user2 = seedUser(jdbc);
            UUID slotA = seedCounter(jdbc, date, 5, 1);
            UUID slotB = seedCounter(jdbc, date, 5, 1);
            ApprovalTestFixtures.seedActiveLock(jdbc, user1, slotA, date);
            ApprovalTestFixtures.seedActiveLock(jdbc, user2, slotB, date);

            race(
                    () -> deliverySlotService.acquireOrSwapLock(user1, slotB, date, java.time.Duration.ofMinutes(15)),
                    () -> deliverySlotService.acquireOrSwapLock(user2, slotA, date, java.time.Duration.ofMinutes(15)));

            assertThat(ApprovalTestFixtures.counterCount(jdbc, slotA, date))
                    .as("iteration %d: slot A capacity-neutral", i).isEqualTo(1);
            assertThat(ApprovalTestFixtures.counterCount(jdbc, slotB, date))
                    .as("iteration %d: slot B capacity-neutral", i).isEqualTo(1);
        }
    }

    @Test
    void opposingSwapConsumedLocks_neverDeadlock_capacityNeutral() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);
        for (int i = 0; i < ITERATIONS; i++) {
            UUID user1 = seedUser(jdbc);
            UUID user2 = seedUser(jdbc);
            UUID slotA = seedCounter(jdbc, date, 5, 1);
            UUID slotB = seedCounter(jdbc, date, 5, 1);
            UUID lockA = seedConsumedLock(jdbc, user1, slotA, date);
            UUID lockB = seedConsumedLock(jdbc, user2, slotB, date);

            race(
                    () -> deliverySlotService.swapConsumedLock(user1, lockA, slotA, date, slotB, date),
                    () -> deliverySlotService.swapConsumedLock(user2, lockB, slotB, date, slotA, date));

            assertThat(ApprovalTestFixtures.counterCount(jdbc, slotA, date))
                    .as("iteration %d: slot A capacity-neutral", i).isEqualTo(1);
            assertThat(ApprovalTestFixtures.counterCount(jdbc, slotB, date))
                    .as("iteration %d: slot B capacity-neutral", i).isEqualTo(1);
        }
    }

    private UUID seedConsumedLock(JdbcTemplate jdbc, UUID userId, UUID slotId, LocalDate date) {
        UUID lockId = UUID.randomUUID();
        jdbc.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at, status) "
                + "VALUES (?, ?, ?, ?, now(), 'CONSUMED')", lockId, userId, slotId, date);
        return lockId;
    }

    /** Both actions must COMPLETE — an aborted victim (deadlock kill) fails the future. */
    private void race(Runnable a, Runnable b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.List<Future<?>> futures = java.util.List.of(
                    pool.submit(() -> {
                        await(start);
                        a.run();
                    }),
                    pool.submit(() -> {
                        await(start);
                        b.run();
                    }));
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
