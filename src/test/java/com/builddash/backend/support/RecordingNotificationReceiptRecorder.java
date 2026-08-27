package com.builddash.backend.support;

import com.builddash.backend.domain.port.NotificationReceiptRecorder;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test-only replacement for LoggingNotificationReceiptRecorder — captures every event the
 * NotificationTriggerListener receives so tests assert directly against what fired
 * (RecordingOtpDispatchQueue convention), no log-scraping.
 */
@Component
@Primary
public class RecordingNotificationReceiptRecorder implements NotificationReceiptRecorder {

    private final List<Object> captured = new ArrayList<>();

    @Override
    public void record(Object event) {
        captured.add(event);
    }

    public List<Object> captured() {
        return Collections.unmodifiableList(captured);
    }

    public void clear() {
        captured.clear();
    }
}
