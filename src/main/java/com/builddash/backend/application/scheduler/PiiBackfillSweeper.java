package com.builddash.backend.application.scheduler;

import com.builddash.backend.domain.port.AddressRepository;
import com.builddash.backend.domain.port.NotificationLogRepository;
import com.builddash.backend.domain.port.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * One-time-and-then-idempotent backfill of the encryption transition (PLAN_PHASE8 decision
 * 4): rows whose PII columns are not yet v1:-prefixed get loaded through the repository
 * (legacy plaintext passes through the converters on read) and re-saved (converters encrypt
 * on write; UserRepositoryAdapter re-populates the blind indexes). Re-running after the
 * backfill finds nothing — the sweep is its own completion proof.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PiiBackfillSweeper {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final NotificationLogRepository notificationLogRepository;

    @Scheduled(fixedDelayString = "${security.pii.backfill-interval-ms:60000}")
    public void sweep() {
        backfillUsers();
        backfillAddresses();
        backfillNotificationLogs();
    }

    private void backfillUsers() {
        List<UUID> ids = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE " +
                        "(phone IS NOT NULL AND phone NOT LIKE 'v1:%') OR " +
                        "(email IS NOT NULL AND email NOT LIKE 'v1:%') OR " +
                        "(google_id IS NOT NULL AND google_id NOT LIKE 'v1:%') OR " +
                        "(name IS NOT NULL AND name NOT LIKE 'v1:%') OR " +
                        "(business_name IS NOT NULL AND business_name NOT LIKE 'v1:%') OR " +
                        "(gst_number IS NOT NULL AND gst_number NOT LIKE 'v1:%') OR " +
                        "(phone_idx IS NULL AND phone IS NOT NULL)",
                UUID.class);
        for (UUID id : ids) {
            try {
                userRepository.findById(id).ifPresent(userRepository::save);
            } catch (Exception e) {
                log.error("Failed to backfill PII for user {}", id, e);
            }
        }
        if (!ids.isEmpty()) {
            log.info("PII backfill: re-encrypted {} user rows", ids.size());
        }
    }

    private void backfillAddresses() {
        List<UUID> ids = jdbcTemplate.queryForList(
                "SELECT id FROM addresses WHERE " +
                        "(line1 IS NOT NULL AND line1 NOT LIKE 'v1:%') OR " +
                        "(line2 IS NOT NULL AND line2 NOT LIKE 'v1:%') OR " +
                        "(lat IS NOT NULL AND lat NOT LIKE 'v1:%') OR " +
                        "(lng IS NOT NULL AND lng NOT LIKE 'v1:%')",
                UUID.class);
        for (UUID id : ids) {
            try {
                addressRepository.findById(id).ifPresent(addressRepository::save);
            } catch (Exception e) {
                log.error("Failed to backfill PII for address {}", id, e);
            }
        }
        if (!ids.isEmpty()) {
            log.info("PII backfill: re-encrypted {} address rows", ids.size());
        }
    }

    private void backfillNotificationLogs() {
        List<UUID> ids = jdbcTemplate.queryForList(
                "SELECT id FROM notification_logs WHERE recipient_phone IS NOT NULL AND recipient_phone NOT LIKE 'v1:%'",
                UUID.class);
        for (UUID id : ids) {
            try {
                notificationLogRepository.findById(id).ifPresent(notificationLogRepository::save);
            } catch (Exception e) {
                log.error("Failed to backfill PII for notification log {}", id, e);
            }
        }
        if (!ids.isEmpty()) {
            log.info("PII backfill: re-encrypted {} notification log rows", ids.size());
        }
    }
}
