package com.builddash.backend.infra.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Per-channel notification queues (PLAN_PHASE7 5(b)): point-to-point via the default
 * exchange (routing key == queue name, otp.dispatch pattern) so an outage on one gateway
 * never blocks another channel's backpressure. All three share ONE dead-letter target —
 * failure semantics are identical across channels, so one inspection point beats three.
 *
 * QueueBuilder fluent style per the locked OQ-1 go-forward standard.
 */
@Configuration
public class NotificationQueueConfig {

    public static final String PUSH_QUEUE_NAME = "notification.push";
    public static final String SMS_QUEUE_NAME = "notification.sms";
    public static final String WHATSAPP_QUEUE_NAME = "notification.whatsapp";
    public static final String DLQ_NAME = "notification.dlq";
    public static final String DLX_NAME = "notification.dlx";

    @Bean
    public Queue notificationPushQueue() {
        return channelQueue(PUSH_QUEUE_NAME);
    }

    @Bean
    public Queue notificationSmsQueue() {
        return channelQueue(SMS_QUEUE_NAME);
    }

    @Bean
    public Queue notificationWhatsAppQueue() {
        return channelQueue(WHATSAPP_QUEUE_NAME);
    }

    private Queue channelQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DLX_NAME)
                .deadLetterRoutingKey(name)
                .build();
    }

    @Bean
    public DirectExchange notificationDeadLetterExchange() {
        return new DirectExchange(DLX_NAME, true, false);
    }

    @Bean
    public Queue notificationDlq() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    public Binding notificationPushDlqBinding() {
        return BindingBuilder.bind(notificationDlq()).to(notificationDeadLetterExchange()).with(PUSH_QUEUE_NAME);
    }

    @Bean
    public Binding notificationSmsDlqBinding() {
        return BindingBuilder.bind(notificationDlq()).to(notificationDeadLetterExchange()).with(SMS_QUEUE_NAME);
    }

    @Bean
    public Binding notificationWhatsAppDlqBinding() {
        return BindingBuilder.bind(notificationDlq()).to(notificationDeadLetterExchange()).with(WHATSAPP_QUEUE_NAME);
    }
}
