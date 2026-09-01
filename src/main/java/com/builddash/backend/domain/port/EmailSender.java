package com.builddash.backend.domain.port;

import java.util.List;

/**
 * Dedicated outbound email port (9-E). Deliberately separate from the notification
 * subsystem — statements email accounting artifacts to the company's designated
 * address; no NotificationChannel, no NotificationLog, no outbox (best-effort delivery).
 */
public interface EmailSender {

    void send(EmailRequest request);

    record EmailRequest(String to, String subject, String body, List<Attachment> attachments) {
    }

    record Attachment(String filename, String contentType, byte[] data) {
    }
}
