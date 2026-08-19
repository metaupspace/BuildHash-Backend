package com.builddash.backend.support;

import com.builddash.backend.domain.port.OtpDispatchQueue;
import com.builddash.backend.domain.port.OtpSender;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Test-only replacement for RabbitOtpDispatchQueue — delivers synchronously in-process instead
 * of publishing to a real broker, so integration tests don't need RabbitMQ running. Delegates to
 * whatever OtpSender is wired (RecordingSmsGateway in tests), simulating instant consumption.
 */
@Component
@Primary
public class RecordingOtpDispatchQueue implements OtpDispatchQueue {

    private final OtpSender otpSender;

    public RecordingOtpDispatchQueue(OtpSender otpSender) {
        this.otpSender = otpSender;
    }

    @Override
    public void enqueue(String phone, String otp) {
        otpSender.send(phone, otp);
    }
}
