package com.builddash.backend.infra.cache;

import com.builddash.backend.domain.port.RateLimiter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Generic RateLimiter over the shared FixedWindowCounter (PLAN_PHASE8 decision 7): key shape
 * ratelimit:&lt;bucket&gt;:&lt;key&gt;, fixed window started by the first request, allowed while
 * count &lt;= limit. Same algorithm as OTP's send counters — one script, two callers.
 */
@Component
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean allow(String bucket, String key, int limit, Duration window) {
        long count = FixedWindowCounter.increment(redis, "ratelimit:" + bucket + ":" + key, window);
        return count <= limit;
    }
}
