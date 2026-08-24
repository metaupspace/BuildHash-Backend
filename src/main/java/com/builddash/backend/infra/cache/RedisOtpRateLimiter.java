package com.builddash.backend.infra.cache;

import com.builddash.backend.infra.config.OtpProperties;
import com.builddash.backend.domain.port.OtpRateLimiter;
import com.builddash.backend.domain.exception.LockedException;
import com.builddash.backend.domain.exception.TooManyRequestsException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class RedisOtpRateLimiter implements OtpRateLimiter {

    /**
     * INCR + PEXPIRE in one atomic Lua call. Two separate commands leave a window
     * where a crash persists the counter without a TTL — the phone then stays
     * rate-locked forever with no self-heal.
     */
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> INCR_WITH_TTL =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                    "local count = redis.call('INCR', KEYS[1]) " +
                            "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end " +
                            "return count",
                    Long.class);

    private final StringRedisTemplate redis;
    private final OtpProperties properties;

    private long incrementWithTtlMillis(String key, long ttlMillis) {
        Long count = redis.execute(INCR_WITH_TTL, List.of(key), String.valueOf(ttlMillis));
        return count == null ? 0L : count;
    }

    @Override
    public void enforceSendAllowed(String phone) {
        String cooldownKey = cooldownKey(phone);
        if (Boolean.TRUE.equals(redis.hasKey(cooldownKey))) {
            throw new TooManyRequestsException("OTP_COOLDOWN", "Please wait before requesting another OTP");
        }

        long sendCount = incrementWithTtlMillis(sendCountKey(phone), Duration.ofHours(1).toMillis());
        if (sendCount > properties.getRateLimitPerHour()) {
            throw new TooManyRequestsException("OTP_RATE_LIMIT_EXCEEDED", "Too many OTP requests for this phone number, try again later");
        }

        if (properties.getSendCooldownSeconds() > 0) {
            redis.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(properties.getSendCooldownSeconds()));
        }
    }

    @Override
    public void enforceNotLockedOut(String phone) {
        String failCountRaw = redis.opsForValue().get(failKey(phone));
        int failCount = failCountRaw == null ? 0 : Integer.parseInt(failCountRaw);
        if (failCount >= properties.getMaxAttempts()) {
            throw new LockedException("OTP_LOCKED", "Too many incorrect attempts, request a new OTP");
        }
    }

    @Override
    public void recordFailedVerification(String phone) {
        incrementWithTtlMillis(failKey(phone), Duration.ofSeconds(properties.getTtlSeconds()).toMillis());
    }

    @Override
    public void resetFailures(String phone) {
        redis.delete(failKey(phone));
    }

    private String failKey(String phone) {
        return "otp_fail:" + phone;
    }

    private String sendCountKey(String phone) {
        return "otp_send_count:" + phone;
    }

    private String cooldownKey(String phone) {
        return "otp_cooldown:" + phone;
    }
}
