package com.builddash.backend.domain.port;

import com.builddash.backend.domain.enums.OtpMatchResult;

import java.time.Duration;

/**
 * SRP: persistence of the current OTP challenge for a phone number — nothing about rate
 * limiting or delivery. Implementations must accept any non-null phone/otp string the way
 * this contract implies (no narrower preconditions), so callers can substitute freely (LSP).
 */
public interface OtpStore {

    void save(String phone, String plainOtp, Duration ttl);

    OtpMatchResult check(String phone, String plainOtp);

    void invalidate(String phone);
}
