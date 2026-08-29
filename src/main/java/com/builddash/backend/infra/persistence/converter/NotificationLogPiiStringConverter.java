package com.builddash.backend.infra.persistence.converter;

/** notification_logs recipient_phone snapshot. */
public class NotificationLogPiiStringConverter extends EncryptedStringConverter {

    @Override
    protected String keyLabel() {
        return "pii:notification-logs:string:aes";
    }
}
