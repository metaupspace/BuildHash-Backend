package com.builddash.backend.application.scheduler;

import com.builddash.backend.support.AbstractIntegrationTest;
import com.builddash.backend.support.ApprovalTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static com.builddash.backend.support.ApprovalTestFixtures.seedCounter;
import static com.builddash.backend.support.ApprovalTestFixtures.seedUser;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * H2.8 on real Postgres: hard-deleting an ACTIVE lock row without transitioning it
 * leaks its held capacity forever. The sweeper must release ACTIVE locks (CAS +
 * decrement) before the delete; a CONSUMED lock belongs to a RETAINed order whose
 * delivery still happens, so its capacity must NOT be decremented again. Deletion
 * itself must never be blocked by the lock handling.
 */
class DeliverySlotCounterLeakOnAccountDeletionJpaIT extends AbstractIntegrationTest {

    @Autowired
    private AccountDeletionSweeper sweeper;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deletion_releasesActiveLockCapacity_keepsConsumedLockCapacity() {
        LocalDate date = LocalDate.now().plusDays(1);
        UUID userId = seedUser(jdbc);
        UUID activeSlotId = seedCounter(jdbc, date, 5, 1);
        UUID consumedSlotId = seedCounter(jdbc, date, 5, 1);
        ApprovalTestFixtures.seedActiveLock(jdbc, userId, activeSlotId, date);
        seedConsumedLock(jdbc, userId, consumedSlotId, date);

        jdbc.update("INSERT INTO delete_requests (id, user_id, requested_at, deletion_scheduled_at, status) "
                + "VALUES (?, ?, now() - interval '31 days', now() - interval '1 hour', 'PENDING')",
                UUID.randomUUID(), userId);

        sweeper.sweep();

        assertThat(ApprovalTestFixtures.counterCount(jdbc, activeSlotId, date))
                .as("ACTIVE lock capacity returns before the row is deleted")
                .isZero();
        assertThat(ApprovalTestFixtures.counterCount(jdbc, consumedSlotId, date))
                .as("CONSUMED lock capacity stays held — its order is RETAINed and still delivers")
                .isEqualTo(1);

        Integer locks = jdbc.queryForObject(
                "SELECT count(*) FROM delivery_slot_locks WHERE user_id = ?", Integer.class, userId);
        assertThat(locks).as("lock rows hard-deleted").isZero();

        String requestStatus = jdbc.queryForObject(
                "SELECT status FROM delete_requests WHERE user_id = ?", String.class, userId);
        assertThat(requestStatus)
                .as("slot handling must not block the deletion")
                .isEqualTo("PROCESSED");
    }

    private void seedConsumedLock(JdbcTemplate jdbc, UUID userId, UUID slotId, LocalDate date) {
        jdbc.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at, status) "
                + "VALUES (?, ?, ?, ?, now(), 'CONSUMED')", UUID.randomUUID(), userId, slotId, date);
    }
}
