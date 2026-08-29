package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.NotificationService;
import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.enums.NotificationStatus;
import com.builddash.backend.domain.model.NotificationLog;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.NotificationDispatchQueue;
import com.builddash.backend.domain.port.NotificationLogRepository;
import com.builddash.backend.domain.port.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbox-style dispatch (catalog-outbox precedent, PLAN_PHASE7 5(c)): guard first, then a
 * PENDING row carrying the recipient phone snapshot, then the queue publish. Skips are
 * logged, never thrown — a missing phone must not fail the caller's transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationLogRepository logRepository;
    private final UserRepository userRepository;
    private final NotificationDispatchQueue dispatchQueue;

    @Override
    @Transactional
    public void notify(UUID userId, NotificationEventType eventType, UUID referenceId) {
        dispatch(userId, eventType, referenceId, null);
    }

    @Override
    @Transactional
    public void notifyRecurring(UUID userId, NotificationEventType eventType, UUID referenceId, Duration cooldown) {
        dispatch(userId, eventType, referenceId, cooldown);
    }

    /**
     * Shared body; the guard is the ONLY divergence. null cooldown = one-way moment
     * (existence check, Checkpoint B behavior byte-identical); a real cooldown = recurring
     * moment (recent-row check).
     */
    private void dispatch(UUID userId, NotificationEventType eventType, UUID referenceId, Duration cooldown) {
        boolean duplicate = cooldown == null
                ? logRepository.existsByEventTypeAndReferenceId(eventType, referenceId)
                : logRepository.existsByEventTypeAndReferenceIdAndCreatedAtAfter(eventType, referenceId, Instant.now().minus(cooldown));
        if (duplicate) {
            log.info("Notification {} for reference {} already covered, skipping duplicate", eventType, referenceId);
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("No user {} to notify for {}, skipping", userId, eventType);
            return;
        }
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            log.info("User {} has no phone, no channel for {}, skipping", userId, eventType);
            return;
        }

        NotificationLog logRow = new NotificationLog();
        logRow.setUserId(userId);
        logRow.setRecipientPhone(user.getPhone());
        logRow.setChannel(eventType.channel());
        logRow.setEventType(eventType);
        logRow.setReferenceId(referenceId);
        logRow.setStatus(NotificationStatus.PENDING);

        NotificationLog saved = logRepository.save(logRow);
        dispatchQueue.enqueue(saved.getId(), saved.getChannel(), saved.getRecipientPhone(), eventType, referenceId);
    }
}
