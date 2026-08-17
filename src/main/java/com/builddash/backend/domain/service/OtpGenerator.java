package com.builddash.backend.domain.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * SRP: only reason to change is the OTP code format (e.g. digit count) — no storage,
 * rate-limiting, or delivery concerns here.
 */
@Component
public class OtpGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String generate(int length) {
        int max = (int) Math.pow(10, length);
        int value = RANDOM.nextInt(max);
        return String.format("%0" + length + "d", value);
    }
}
