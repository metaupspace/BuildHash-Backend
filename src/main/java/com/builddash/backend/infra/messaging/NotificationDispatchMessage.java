package com.builddash.backend.infra.messaging;

import com.builddash.backend.domain.enums.NotificationChannel;
import com.builddash.backend.domain.enums.NotificationEventType;

import java.util.UUID;

/**
 * Wire payload for the per-channel dispatch queues (PLAN_PHASE7 Section 8 shape:
 * {logId, channel, phone, eventType, referenceId}). Kept separate from the sender ports'
 * plain parameters so envelope fields can grow without touching the port contracts
 * (OtpDispatchMessage convention).
 */
public record NotificationDispatchMessage(
        UUID logId,
        NotificationChannel channel,
        String phone,
        NotificationEventType eventType,
        UUID referenceId
) {}
