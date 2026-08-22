package com.builddash.backend.infra.messaging;

import com.builddash.backend.domain.port.OtpDispatchQueue;
import com.builddash.backend.infra.config.OtpQueueConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class RabbitOtpDispatchQueue implements OtpDispatchQueue {

    private final RabbitTemplate rabbitTemplate;


    @Override
    public void enqueue(String phone, String otp) {
        rabbitTemplate.convertAndSend(OtpQueueConfig.QUEUE_NAME, new OtpDispatchMessage(phone, otp));
    }
}
