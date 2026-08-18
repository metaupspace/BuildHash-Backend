package com.builddash.backend.infra.cache;

import com.builddash.backend.infra.config.OtpProperties;
import com.builddash.backend.domain.port.OtpRateLimiter;
import com.builddash.backend.domain.exception.LockedException;
import com.builddash.backend.domain.exception.TooManyRequestsException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisOtpRateLimiter implements OtpRateLimiter {

    private final StringRedisTemplate redis;
    private final OtpProperties properties;

    public RedisOtpRateLimiter(StringRedisTemplate redis, OtpProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public void enforceSendAllowed(String phone) {
        String cooldownKey = cooldownKey(phone);
        if (Boolean.TRUE.equals(redis.hasKey(cooldownKey))) {
            throw new TooManyRequestsException("OTP_COOLDOWN", "Please wait before requesting another OTP");
        }

        String sendCountKey = sendCountKey(phone);
        Long sendCount = redis.opsForValue().increment(sendCountKey);
        if (sendCount != null && sendCount == 1L) {
            redis.expire(sendCountKey, Duration.ofHours(1));
        }
        if (sendCount != null && sendCount > properties.getRateLimitPerHour()) {
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
        String failKey = failKey(phone);
        Long newFailCount = redis.opsForValue().increment(failKey);
        if (newFailCount != null && newFailCount == 1L) {
            redis.expire(failKey, Duration.ofSeconds(properties.getTtlSeconds()));
        }
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
