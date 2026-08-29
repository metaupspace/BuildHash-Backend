package com.builddash.backend.infra.external;

import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.port.PushNotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stub push channel — no FCM/APNs token infrastructure exists anywhere (PLAN_PHASE7 Fact 10),
 * so the "send" is a dev log line. Swap in a real provider by adding another
 * PushNotificationSender implementation, per OCP.
 */
@Component
@Profile("!prod")
@Slf4j
public class LoggingPushNotificationSender implements PushNotificationSender {

    @Override
    public void send(String recipient, NotificationEventType eventType, UUID referenceId) {
        log.info(">>> [DEV PUSH] to={} eventType={} referenceId={}", recipient, eventType, referenceId);
    }
}
