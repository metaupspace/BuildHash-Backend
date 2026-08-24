package com.builddash.backend.infra.cache;

import com.builddash.backend.infra.config.OtpProperties;
import com.builddash.backend.domain.exception.LockedException;
import com.builddash.backend.domain.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisOtpRateLimiterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private OtpProperties properties;
    private RedisOtpRateLimiter limiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        properties = new OtpProperties();
        properties.setRateLimitPerHour(5);
        properties.setMaxAttempts(3);
        properties.setSendCooldownSeconds(60);
        properties.setTtlSeconds(300);
        limiter = new RedisOtpRateLimiter(redis, properties);
    }

    @Test
    void enforceSendAllowed_counterIncrementIsAtomicSingleScript() {
        // INCR + EXPIRE in one Lua script: a crash between two separate calls would
        // leave the key without a TTL and lock the phone out forever
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        assertThatCode(() -> limiter.enforceSendAllowed("+911111100099")).doesNotThrowAnyException();

        verify(redis).execute(any(RedisScript.class), eq(List.of("otp_send_count:+911111100099")), eq("3600000"));
        verify(valueOps, never()).increment(anyString());
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void enforceSendAllowed_overLimit_throws() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(6L);

        assertThatThrownBy(() -> limiter.enforceSendAllowed("+911111100099"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void recordFailedVerification_counterIncrementIsAtomicSingleScript() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        assertThatCode(() -> limiter.recordFailedVerification("+911111100099")).doesNotThrowAnyException();

        verify(redis).execute(any(RedisScript.class), eq(List.of("otp_fail:+911111100099")), eq("300000"));
        verify(valueOps, never()).increment(anyString());
    }

    @Test
    void enforceNotLockedOut_atMaxAttempts_throws() {
        when(valueOps.get("otp_fail:+911111100099")).thenReturn("3");

        assertThatThrownBy(() -> limiter.enforceNotLockedOut("+911111100099"))
                .isInstanceOf(LockedException.class);
    }
}
