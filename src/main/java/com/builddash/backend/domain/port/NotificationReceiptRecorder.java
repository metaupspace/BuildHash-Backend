package com.builddash.backend.domain.port;

/**
 * Phase 7 Checkpoint A receipt seam: the NotificationTriggerListener hands every received event to
 * this port. Production logs it (LoggingNotificationReceiptRecorder); Checkpoint B replaces the
 * listener's record() calls with real dispatch. Test contexts swap in a recording fake
 * (RecordingOtpDispatchQueue convention) so "the event fired" is asserted directly, not scraped
 * from logs.
 */
public interface NotificationReceiptRecorder {

    void record(Object event);
}
