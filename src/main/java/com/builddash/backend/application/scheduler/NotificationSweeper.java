package com.builddash.backend.application.scheduler;

import com.builddash.backend.domain.model.NotificationLog;
import com.builddash.backend.domain.port.NotificationDispatchQueue;
import com.builddash.backend.domain.port.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The outbox's self-heal half — CatalogOutboxRelay shape exactly (single class, no separate
 * port: single workflow, single caller, the scheduler). A PENDING row older than the stuck
 * threshold is a lost confirm (broker retry exhausts in seconds; anything older means the
 * consumer never acked), so it is re-enqueued — NOT marked FAILED, which stays the DLQ
 * listener's call. Rows settle when the consumer's markSent lands; re-enqueue may
 * duplicate a send (at-least-once), accepted for a best-effort nudge channel.
 *
 * H5.8: Daily retention cleanup of terminal (SENT/FAILED) logs older than 30 days.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationSweeper {

    private final NotificationLogRepository logRepository;
    private final NotificationDispatchQueue dispatchQueue;

    @Value("${notification.sweep.stuck-after-minutes:10}")
    private long stuckAfterMinutes;

    @Scheduled(fixedDelayString = "${notification.sweep.interval-ms:60000}")
    public void sweep() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(stuckAfterMinutes));
        List<NotificationLog> stuck = logRepository.findStalePending(cutoff);
        for (NotificationLog row : stuck) {
            sweepOne(row);
        }
    }

    private void sweepOne(NotificationLog row) {
        try {
            dispatchQueue.enqueue(row.getId(), row.getChannel(), row.getRecipientPhone(), row.getEventType(), row.getReferenceId());
        } catch (Exception e) {
            // Will retry next poll — one failing enqueue never blocks sibling rows.
            log.error("Failed to re-enqueue stuck notification {}, will retry next poll", row.getId(), e);
        }
    }

    @Scheduled(cron = "${notification.cleanup.cron:0 0 3 * * *}")
    public void cleanupOldLogs() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(30));
        int deleted = logRepository.deleteTerminalLogsOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} terminal notification logs older than 30 days", deleted);
        }
    }
}
