package com.builddash.backend.application.impl;

import com.builddash.backend.domain.service.OtpGenerator;
import com.builddash.backend.domain.port.OtpConfig;
import com.builddash.backend.domain.port.OtpDispatchQueue;
import com.builddash.backend.domain.port.OtpRateLimiter;
import com.builddash.backend.domain.port.OtpStore;
import org.springframework.stereotype.Service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;

/**
 * SRP: the "send an OTP" workflow only — generation, rate-limit policy, storage, and delivery
 * are each a distinct collaborator injected by interface (DIP).
 */
@RequiredArgsConstructor
@Service
public class OtpSendService {

    private final OtpRateLimiter rateLimiter;
    private final OtpGenerator generator;
    private final OtpStore store;
    private final OtpDispatchQueue dispatchQueue;
    private final OtpConfig config;


    public void send(String phone) {
        rateLimiter.enforceSendAllowed(phone);

        String otp = generator.generate(config.getLength());
        store.save(phone, otp, Duration.ofSeconds(config.getTtlSeconds()));
        rateLimiter.resetFailures(phone);

        dispatchQueue.enqueue(phone, otp);
    }
}
