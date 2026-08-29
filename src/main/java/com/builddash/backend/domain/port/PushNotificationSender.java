package com.builddash.backend.domain.port;

import com.builddash.backend.domain.enums.NotificationEventType;

import java.util.UUID;

/**
 * OCP: the push channel (stub today, FCM/APNs in the real-vendor phase) is a new
 * implementation of this interface — nothing upstream changes to add one. One interface per
 * channel on purpose (OtpSender precedent): a channel parameter would force a switch in
 * every adapter.
 */
public interface PushNotificationSender {

    void send(String recipient, NotificationEventType eventType, UUID referenceId);
}
