package com.builddash.backend.application.impl;

import com.builddash.backend.domain.service.OtpGenerator;
import com.builddash.backend.infra.config.OtpProperties;
import com.builddash.backend.domain.port.OtpDispatchQueue;
import com.builddash.backend.domain.port.OtpRateLimiter;
import com.builddash.backend.domain.port.OtpStore;
import com.builddash.backend.domain.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpSendServiceTest {

    private static final String PHONE = "+919876543210";

    @Mock
    private OtpRateLimiter rateLimiter;
    @Mock
    private OtpGenerator generator;
    @Mock
    private OtpStore store;
    @Mock
    private OtpDispatchQueue dispatchQueue;

    private OtpProperties properties;
    private OtpSendService sendService;

    @BeforeEach
    void setUp() {
        properties = new OtpProperties();
        properties.setLength(6);
        properties.setTtlSeconds(300);
        sendService = new OtpSendService(rateLimiter, generator, store, dispatchQueue, properties);
    }

    @Test
    void send_happyPath_generatesStoresResetsAndDispatches() {
        when(generator.generate(6)).thenReturn("123456");

        sendService.send(PHONE);

        verify(rateLimiter).enforceSendAllowed(PHONE);
        verify(store).save(eq(PHONE), eq("123456"), eq(Duration.ofSeconds(300)));
        verify(rateLimiter).resetFailures(PHONE);
        verify(dispatchQueue).enqueue(PHONE, "123456");
    }

    @Test
    void send_rateLimited_neverGeneratesOrDispatches() {
        org.mockito.Mockito.doThrow(new TooManyRequestsException("OTP_COOLDOWN", "wait"))
                .when(rateLimiter).enforceSendAllowed(PHONE);

        assertThatThrownBy(() -> sendService.send(PHONE))
                .isInstanceOf(TooManyRequestsException.class);

        verify(generator, never()).generate(any(Integer.class));
        verify(store, never()).save(anyString(), anyString(), any());
        verify(dispatchQueue, never()).enqueue(anyString(), anyString());
    }
}
