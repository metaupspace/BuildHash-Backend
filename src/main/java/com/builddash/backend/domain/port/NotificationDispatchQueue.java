package com.builddash.backend.domain.port;

import com.builddash.backend.domain.enums.NotificationChannel;
import com.builddash.backend.domain.enums.NotificationEventType;

import java.util.UUID;

/**
 * Publish-side seam for the per-channel dispatch queues (OtpDispatchQueue pattern): the
 * application layer enqueues without knowing queue names or the broker.
 */
public interface NotificationDispatchQueue {

    void enqueue(UUID logId, NotificationChannel channel, String recipientPhone, NotificationEventType eventType, UUID referenceId);
}
