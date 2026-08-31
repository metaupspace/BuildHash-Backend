package com.builddash.backend.domain.port;

import java.time.Duration;

/**
 * Generic fixed-window rate limiting (PLAN_PHASE8 decision 7). Callers name their bucket,
 * supply the subject key (e.g. client IP), and their own limit/window from configuration —
 * the port stays mechanism-agnostic. OTP's limiter (OtpRateLimiter) is deliberately NOT this
 * port: its semantics (cooldown + send-per-hour + lockout) are OTP-specific.
 */
public interface RateLimiter {

    /**
     * Records one request against {@code bucket}/{@code key} and reports whether it is within
     * {@code limit} for the current {@code window}. The first request in a window starts it
     * (fixed window, TTL-backed — the same algorithm as OTP's send counters).
     */
    boolean allow(String bucket, String key, int limit, Duration window);
}
