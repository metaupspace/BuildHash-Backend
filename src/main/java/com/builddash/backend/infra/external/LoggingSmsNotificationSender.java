package com.builddash.backend.infra.external;

import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.port.SmsNotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stub SMS channel (SmsOtpSender pattern) — no real provider in Phase 7, the send is a dev
 * log line. No producer routes here until the Checkpoint C cart-abandonment job.
 */
@Component
@Profile("!prod")
@Slf4j
public class LoggingSmsNotificationSender implements SmsNotificationSender {

    @Override
    public void send(String recipient, NotificationEventType eventType, UUID referenceId) {
        log.info(">>> [DEV SMS] to={} eventType={} referenceId={}", recipient, eventType, referenceId);
    }
}
