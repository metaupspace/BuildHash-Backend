package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.OtpMatchResult;
import com.builddash.backend.domain.port.OtpRateLimiter;
import com.builddash.backend.domain.port.OtpStore;
import com.builddash.backend.domain.exception.BadRequestException;
import com.builddash.backend.domain.exception.LockedException;
import com.builddash.backend.domain.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpVerificationServiceTest {

    private static final String PHONE = "+919876543210";
    private static final String OTP = "123456";

    @Mock
    private OtpRateLimiter rateLimiter;
    @Mock
    private OtpStore store;

    private OtpVerificationService service() {
        return new OtpVerificationService(rateLimiter, store);
    }

    @Test
    void verify_matchingCode_invalidatesAndResetsFailures() {
        when(store.check(PHONE, OTP)).thenReturn(OtpMatchResult.MATCH);

        service().verify(PHONE, OTP);

        verify(store).invalidate(PHONE);
        verify(rateLimiter).resetFailures(PHONE);
        verify(rateLimiter, never()).recordFailedVerification(PHONE);
    }

    @Test
    void verify_wrongCode_recordsFailureAndThrowsUnauthorized() {
        when(store.check(PHONE, OTP)).thenReturn(OtpMatchResult.MISMATCH);

        assertThatThrownBy(() -> service().verify(PHONE, OTP))
                .isInstanceOf(UnauthorizedException.class);

        verify(rateLimiter).recordFailedVerification(PHONE);
        verify(store, never()).invalidate(PHONE);
    }

    @Test
    void verify_noOtpStored_throwsBadRequestWithoutRecordingFailure() {
        when(store.check(PHONE, OTP)).thenReturn(OtpMatchResult.NOT_FOUND);

        assertThatThrownBy(() -> service().verify(PHONE, OTP))
                .isInstanceOf(BadRequestException.class);

        verify(rateLimiter, never()).recordFailedVerification(PHONE);
    }

    @Test
    void verify_lockedOut_neverChecksStore() {
        org.mockito.Mockito.doThrow(new LockedException("OTP_LOCKED", "locked"))
                .when(rateLimiter).enforceNotLockedOut(PHONE);

        assertThatThrownBy(() -> service().verify(PHONE, OTP))
                .isInstanceOf(LockedException.class);

        verify(store, never()).check(PHONE, OTP);
    }
}
