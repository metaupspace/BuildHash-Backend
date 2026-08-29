package com.builddash.backend.infra.external;

import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.port.WhatsAppNotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stub WhatsApp channel — WhatsApp Business API integration is the real-vendor phase; the
 * feature doc's "submit templates to Meta early" is an ops TODO recorded in the plan, not
 * code. Swap in a real provider by adding another WhatsAppNotificationSender, per OCP.
 */
@Component
@Profile("!prod")
@Slf4j
public class LoggingWhatsAppNotificationSender implements WhatsAppNotificationSender {

    @Override
    public void send(String recipient, NotificationEventType eventType, UUID referenceId) {
        log.info(">>> [DEV WHATSAPP] to={} eventType={} referenceId={}", recipient, eventType, referenceId);
    }
}
