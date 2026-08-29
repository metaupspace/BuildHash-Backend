package com.builddash.backend.infra.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationQueueConfigTest {

    private final NotificationQueueConfig config = new NotificationQueueConfig();

    @Test
    void pushQueue_hasDeadLetterRouting() {
        Queue queue = config.notificationPushQueue();

        assertThat(queue.getName()).isEqualTo("notification.push");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "notification.dlx")
                .containsEntry("x-dead-letter-routing-key", "notification.push");
    }

    @Test
    void smsQueue_hasDeadLetterRouting() {
        Queue queue = config.notificationSmsQueue();

        assertThat(queue.getName()).isEqualTo("notification.sms");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "notification.dlx")
                .containsEntry("x-dead-letter-routing-key", "notification.sms");
    }

    @Test
    void whatsAppQueue_hasDeadLetterRouting() {
        Queue queue = config.notificationWhatsAppQueue();

        assertThat(queue.getName()).isEqualTo("notification.whatsapp");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "notification.dlx")
                .containsEntry("x-dead-letter-routing-key", "notification.whatsapp");
    }

    @Test
    void dlq_declaredDurable() {
        Queue dlq = config.notificationDlq();

        assertThat(dlq.getName()).isEqualTo("notification.dlq");
        assertThat(dlq.isDurable()).isTrue();
    }

    @Test
    void dlqBindings_routeEachChannelKeyToSharedDlq() {
        var push = config.notificationPushDlqBinding();
        var sms = config.notificationSmsDlqBinding();
        var whatsApp = config.notificationWhatsAppDlqBinding();

        assertThat(push.getExchange()).isEqualTo("notification.dlx");
        assertThat(push.getDestination()).isEqualTo("notification.dlq");
        assertThat(push.getRoutingKey()).isEqualTo("notification.push");

        assertThat(sms.getExchange()).isEqualTo("notification.dlx");
        assertThat(sms.getDestination()).isEqualTo("notification.dlq");
        assertThat(sms.getRoutingKey()).isEqualTo("notification.sms");

        assertThat(whatsApp.getExchange()).isEqualTo("notification.dlx");
        assertThat(whatsApp.getDestination()).isEqualTo("notification.dlq");
        assertThat(whatsApp.getRoutingKey()).isEqualTo("notification.whatsapp");
    }
}
