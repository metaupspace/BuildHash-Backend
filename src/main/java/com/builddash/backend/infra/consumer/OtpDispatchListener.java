package com.builddash.backend.infra.consumer;

import com.builddash.backend.domain.port.OtpSender;
import com.builddash.backend.infra.config.OtpQueueConfig;
import com.builddash.backend.infra.messaging.OtpDispatchMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * The consumer side of the queue — delegates to OtpSender, the existing channel abstraction,
 * so adding a real SMS provider later only ever means a new OtpSender implementation.
 */
@Component
public class OtpDispatchListener {

    private final OtpSender otpSender;

    public OtpDispatchListener(OtpSender otpSender) {
        this.otpSender = otpSender;
    }

    @RabbitListener(queues = OtpQueueConfig.QUEUE_NAME)
    public void onMessage(OtpDispatchMessage message) {
        otpSender.send(message.phone(), message.otp());
    }
}
