package com.builddash.backend.infra.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class OtpQueueConfigTest {

    private final OtpQueueConfig config = new OtpQueueConfig();

    @Test
    void dispatchQueue_hasDeadLetterRouting() {
        Queue queue = config.otpDispatchQueue();

        assertThat(queue.getName()).isEqualTo("otp.dispatch");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "otp.dlx")
                .containsEntry("x-dead-letter-routing-key", "otp.dispatch");
    }

    @Test
    void dlq_declaredDurable() {
        Queue dlq = config.otpDispatchDlq();

        assertThat(dlq.getName()).isEqualTo("otp.dispatch.dlq");
        assertThat(dlq.isDurable()).isTrue();
    }

    @Test
    void dlqBinding_routesDispatchKeyToDlq() {
        var binding = config.otpDlqBinding();

        assertThat(binding.getExchange()).isEqualTo("otp.dlx");
        assertThat(binding.getRoutingKey()).isEqualTo("otp.dispatch");
        assertThat(binding.getDestination()).isEqualTo("otp.dispatch.dlq");
    }
}
