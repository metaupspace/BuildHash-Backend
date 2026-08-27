package com.builddash.backend.infra.external;

import com.builddash.backend.domain.port.NotificationReceiptRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Checkpoint A production behavior — receipt is observable only via logs until Checkpoint B wires
 * real notification dispatch. Stub-placement follows StubImageSearchProvider (infra/external).
 */
@Component
@Slf4j
public class LoggingNotificationReceiptRecorder implements NotificationReceiptRecorder {

    @Override
    public void record(Object event) {
        log.info("Notification trigger received: {}", event);
    }
}
