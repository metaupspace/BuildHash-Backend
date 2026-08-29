package com.builddash.backend.domain.port;

import com.builddash.backend.domain.enums.NotificationEventType;

import java.util.UUID;

/**
 * OCP: the WhatsApp channel (stub today, WhatsApp Business API in the real-vendor phase)
 * is a new implementation of this interface — nothing upstream changes to add one.
 */
public interface WhatsAppNotificationSender {

    void send(String recipient, NotificationEventType eventType, UUID referenceId);
}
