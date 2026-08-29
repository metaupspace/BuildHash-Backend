package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.NotificationChannel;
import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.enums.NotificationStatus;
import com.builddash.backend.infra.persistence.entity.NotificationLogEntity;
import com.builddash.backend.infra.persistence.repository.NotificationLogJpaRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Plan Section 7's sweep-reconcile JpaIT: a stuck PENDING row past the threshold is
 * re-enqueued (and left PENDING — settling is the consumer's markSent), a fresh PENDING
 * row is untouched. Queue port mocked so the sweep's enqueue is directly countable and
 * no live broker or racing consumer interferes.
 */
class NotificationSweepJpaIT extends AbstractIntegrationTest {

    private static final java.util.concurrent.atomic.AtomicInteger SEQ = new java.util.concurrent.atomic.AtomicInteger();

    @Autowired
    private com.builddash.backend.application.scheduler.NotificationSweeper sweeper;

    @Autowired
    private NotificationLogJpaRepository notificationLogJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private com.builddash.backend.domain.port.NotificationDispatchQueue dispatchQueue;

    private UUID seedPendingRow(long minutesAgo) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, phone) VALUES (?, ?)", userId, "+9199" + String.format("%08d", SEQ.incrementAndGet()));
        NotificationLogEntity row = new NotificationLogEntity();
        row.setUserId(userId);
        row.setRecipientPhone("+911234567890");
        row.setChannel(NotificationChannel.WHATSAPP);
        row.setEventType(NotificationEventType.ORDER_PACKED);
        row.setReferenceId(UUID.randomUUID());
        NotificationLogEntity saved = notificationLogJpaRepository.save(row);

        jdbcTemplate.update("UPDATE notification_logs SET created_at = now() - ?::interval WHERE id = ?",
                minutesAgo + " minutes", saved.getId());

        Instant readBack = jdbcTemplate.queryForObject(
                "SELECT created_at FROM notification_logs WHERE id = ?", Instant.class, saved.getId());
        assertThat(readBack).isBefore(Instant.now().minusSeconds((minutesAgo - 1) * 60));
        return saved.getId();
    }

    @Test
    void stuckRowReenqueuedAndStillPending_freshRowUntouched() {
        UUID stuckId = seedPendingRow(30);
        UUID freshId = seedPendingRow(1);

        sweeper.sweep();

        verify(dispatchQueue).enqueue(eq(stuckId), eq(NotificationChannel.WHATSAPP), eq("+911234567890"),
                eq(NotificationEventType.ORDER_PACKED), any());
        verify(dispatchQueue, never()).enqueue(eq(freshId), any(), any(), any(), any());

        assertThat(notificationLogJpaRepository.findById(stuckId).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.PENDING);
        assertThat(notificationLogJpaRepository.findById(freshId).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.PENDING);
    }
}
