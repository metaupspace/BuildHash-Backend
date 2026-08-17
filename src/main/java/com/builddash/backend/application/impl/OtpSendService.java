package com.builddash.backend.application.impl;

import com.builddash.backend.domain.service.OtpGenerator;
import com.builddash.backend.infra.config.OtpProperties;
import com.builddash.backend.domain.port.OtpDispatchQueue;
import com.builddash.backend.domain.port.OtpRateLimiter;
import com.builddash.backend.domain.port.OtpStore;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * SRP: the "send an OTP" workflow only — generation, rate-limit policy, storage, and delivery
 * are each a distinct collaborator injected by interface (DIP).
 */
@Service
public class OtpSendService {

    private final OtpRateLimiter rateLimiter;
    private final OtpGenerator generator;
    private final OtpStore store;
    private final OtpDispatchQueue dispatchQueue;
    private final OtpProperties properties;

    public OtpSendService(OtpRateLimiter rateLimiter, OtpGenerator generator, OtpStore store,
                           OtpDispatchQueue dispatchQueue, OtpProperties properties) {
        this.rateLimiter = rateLimiter;
        this.generator = generator;
        this.store = store;
        this.dispatchQueue = dispatchQueue;
        this.properties = properties;
    }

    public void send(String phone) {
        rateLimiter.enforceSendAllowed(phone);

        String otp = generator.generate(properties.getLength());
        store.save(phone, otp, Duration.ofSeconds(properties.getTtlSeconds()));
        rateLimiter.resetFailures(phone);

        dispatchQueue.enqueue(phone, otp);
    }
}
