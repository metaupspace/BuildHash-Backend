package com.builddash.backend.infra.messaging;

import com.builddash.backend.domain.enums.NotificationChannel;
import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.port.NotificationDispatchQueue;
import com.builddash.backend.infra.config.NotificationQueueConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RabbitNotificationDispatchQueue implements NotificationDispatchQueue {

    private final RabbitTemplate rabbitTemplate;


    @Override
    public void enqueue(UUID logId, NotificationChannel channel, String recipientPhone, NotificationEventType eventType, UUID referenceId) {
        rabbitTemplate.convertAndSend(queueNameFor(channel),
                new NotificationDispatchMessage(logId, channel, recipientPhone, eventType, referenceId));
    }

    /**
     * Channel-to-queue addressing lives here and only here — queue names are wire config,
     * not domain knowledge. This is the one permitted channel branch: it selects an address,
     * it does not select behavior.
     */
    private String queueNameFor(NotificationChannel channel) {
        return switch (channel) {
            case PUSH -> NotificationQueueConfig.PUSH_QUEUE_NAME;
            case SMS -> NotificationQueueConfig.SMS_QUEUE_NAME;
            case WHATSAPP -> NotificationQueueConfig.WHATSAPP_QUEUE_NAME;
        };
    }
}
