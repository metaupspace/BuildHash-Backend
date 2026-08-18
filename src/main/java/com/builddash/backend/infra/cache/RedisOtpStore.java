package com.builddash.backend.infra.cache;

import com.builddash.backend.domain.enums.OtpMatchResult;
import com.builddash.backend.domain.port.OtpStore;
import com.builddash.backend.common.Sha256;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisOtpStore implements OtpStore {

    private final StringRedisTemplate redis;

    public RedisOtpStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String phone, String plainOtp, Duration ttl) {
        redis.opsForValue().set(otpKey(phone), Sha256.hex(plainOtp), ttl);
    }

    @Override
    public OtpMatchResult check(String phone, String plainOtp) {
        String storedHash = redis.opsForValue().get(otpKey(phone));
        if (storedHash == null) {
            return OtpMatchResult.NOT_FOUND;
        }
        return storedHash.equals(Sha256.hex(plainOtp)) ? OtpMatchResult.MATCH : OtpMatchResult.MISMATCH;
    }

    @Override
    public void invalidate(String phone) {
        redis.delete(otpKey(phone));
    }

    private String otpKey(String phone) {
        return "otp:" + phone;
    }
}
