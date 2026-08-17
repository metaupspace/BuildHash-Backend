package com.builddash.backend.domain.port;

import com.builddash.backend.domain.exception.LockedException;
import com.builddash.backend.domain.exception.TooManyRequestsException;

/**
 * OCP: the send-cooldown/hourly-cap and wrong-attempt-lockout POLICY lives entirely behind
 * this interface. A new policy (sliding window, per-IP, etc.) is a new implementation class —
 * OtpSendService/OtpVerificationService never change.
 */
public interface OtpRateLimiter {

    /**
     * Throws TooManyRequestsException if a send isn't currently allowed; otherwise records
     * this send attempt (starts the cooldown, counts against the hourly cap) as a side effect.
     */
    void enforceSendAllowed(String phone) throws TooManyRequestsException;

    /**
     * Throws LockedException if this phone has exceeded the wrong-attempt limit.
     */
    void enforceNotLockedOut(String phone) throws LockedException;

    void recordFailedVerification(String phone);

    /**
     * Clears prior wrong-attempt state — called both when a fresh OTP is sent and after a
     * successful verification.
     */
    void resetFailures(String phone);
}
