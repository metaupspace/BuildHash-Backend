package com.builddash.backend.infra.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the durable queue used for async OTP dispatch, published to via the default exchange
 * (routing key == queue name) — a second exchange/binding isn't warranted for one point-to-point
 * queue.
 *
 * Retry exhaustion (listener recovers with RejectAndDontRequeueRecoverer after 3 attempts)
 * dead-letters into otp.dispatch.dlq instead of silently dropping the message — an SMS
 * provider outage then leaves inspectable messages rather than users waiting forever.
 *
 * NOTE: redeclaring otp.dispatch with DLX args on a broker that already has the queue
 * without them fails with PRECONDITION_FAILED — delete the old queue once when upgrading.
 */
@Configuration
public class OtpQueueConfig {

    public static final String QUEUE_NAME = "otp.dispatch";
    public static final String DLQ_NAME = "otp.dispatch.dlq";
    public static final String DLX_NAME = "otp.dlx";

    @Bean
    public Queue otpDispatchQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .deadLetterExchange(DLX_NAME)
                .deadLetterRoutingKey(QUEUE_NAME)
                .build();
    }

    @Bean
    public DirectExchange otpDeadLetterExchange() {
        return new DirectExchange(DLX_NAME, true, false);
    }

    @Bean
    public Queue otpDispatchDlq() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    public Binding otpDlqBinding() {
        return BindingBuilder.bind(otpDispatchDlq()).to(otpDeadLetterExchange()).with(QUEUE_NAME);
    }
}
