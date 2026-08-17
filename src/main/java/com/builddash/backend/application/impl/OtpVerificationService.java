package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.OtpMatchResult;
import com.builddash.backend.domain.port.OtpRateLimiter;
import com.builddash.backend.domain.port.OtpStore;
import com.builddash.backend.domain.exception.BadRequestException;
import com.builddash.backend.domain.exception.UnauthorizedException;
import org.springframework.stereotype.Service;

/**
 * SRP: the "verify an OTP" workflow only — no generation or delivery concerns.
 */
@Service
public class OtpVerificationService {

    private final OtpRateLimiter rateLimiter;
    private final OtpStore store;

    public OtpVerificationService(OtpRateLimiter rateLimiter, OtpStore store) {
        this.rateLimiter = rateLimiter;
        this.store = store;
    }

    public void verify(String phone, String otp) {
        rateLimiter.enforceNotLockedOut(phone);

        OtpMatchResult result = store.check(phone, otp);
        switch (result) {
            case NOT_FOUND -> throw new BadRequestException("OTP_EXPIRED", "OTP has expired or was not requested");
            case MISMATCH -> {
                rateLimiter.recordFailedVerification(phone);
                throw new UnauthorizedException("OTP_INCORRECT", "Incorrect OTP");
            }
            case MATCH -> {
                store.invalidate(phone);
                rateLimiter.resetFailures(phone);
            }
        }
    }
}
