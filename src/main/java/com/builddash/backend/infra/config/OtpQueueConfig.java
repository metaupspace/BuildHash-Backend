package com.builddash.backend.infra.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the durable queue used for async OTP dispatch, published to via the default exchange
 * (routing key == queue name) — a second exchange/binding isn't warranted for one point-to-point
 * queue.
 */
@Configuration
public class OtpQueueConfig {

    public static final String QUEUE_NAME = "otp.dispatch";

    @Bean
    public Queue otpDispatchQueue() {
        return new Queue(QUEUE_NAME, true);
    }
}
