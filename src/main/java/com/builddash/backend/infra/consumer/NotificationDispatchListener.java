package com.builddash.backend.infra.consumer;

import com.builddash.backend.domain.port.NotificationLogRepository;
import com.builddash.backend.domain.port.PushNotificationSender;
import com.builddash.backend.domain.port.SmsNotificationSender;
import com.builddash.backend.domain.port.WhatsAppNotificationSender;
import com.builddash.backend.infra.config.NotificationQueueConfig;
import com.builddash.backend.infra.messaging.NotificationDispatchMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * The consumer half of the outbox: one @RabbitListener per channel queue, each delegating
 * to exactly its own sender port, then flipping the log row SENT — the async-confirm hop.
 * A sender throw propagates on purpose: broker retry (3 attempts) owns retries, and
 * exhaustion dead-letters into notification.dlq where the DLQ handler flips the row FAILED.
 * One class, not three, mirrors the locked consolidation decision behind
 * NotificationTriggerListener.
 */
@Component
@RequiredArgsConstructor
public class NotificationDispatchListener {

    private final PushNotificationSender pushSender;
    private final SmsNotificationSender smsSender;
    private final WhatsAppNotificationSender whatsAppSender;
    private final NotificationLogRepository logRepository;


    @RabbitListener(queues = NotificationQueueConfig.WHATSAPP_QUEUE_NAME)
    public void onWhatsApp(NotificationDispatchMessage message) {
        whatsAppSender.send(message.phone(), message.eventType(), message.referenceId());
        logRepository.markSent(message.logId());
    }

    @RabbitListener(queues = NotificationQueueConfig.SMS_QUEUE_NAME)
    public void onSms(NotificationDispatchMessage message) {
        smsSender.send(message.phone(), message.eventType(), message.referenceId());
        logRepository.markSent(message.logId());
    }

    @RabbitListener(queues = NotificationQueueConfig.PUSH_QUEUE_NAME)
    public void onPush(NotificationDispatchMessage message) {
        pushSender.send(message.phone(), message.eventType(), message.referenceId());
        logRepository.markSent(message.logId());
    }

    @RabbitListener(queues = NotificationQueueConfig.DLQ_NAME)
    public void onDeadLetter(NotificationDispatchMessage message) {
        logRepository.markFailed(message.logId());
    }
}
