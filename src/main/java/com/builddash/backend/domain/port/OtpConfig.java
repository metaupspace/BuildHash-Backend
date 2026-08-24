package com.builddash.backend.domain.port;

public interface OtpConfig {
    int getLength();
    long getTtlSeconds();
    int getMaxAttempts();
    long getSendCooldownSeconds();
    long getRateLimitPerHour();
}
