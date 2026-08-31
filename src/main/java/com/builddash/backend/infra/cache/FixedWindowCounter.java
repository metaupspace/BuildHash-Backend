package com.builddash.backend.infra.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

/**
 * The single home of the atomic fixed-window counter (extracted from RedisOtpRateLimiter,
 * PLAN_PHASE8 Checkpoint B): INCR + PEXPIRE in one Lua call, TTL set only on the first
 * increment — the first request in a window starts it. Two separate commands would leave a
 * crash window where the counter persists without a TTL and the subject stays rate-locked
 * forever with no self-heal. Both RedisOtpRateLimiter and RedisRateLimiter call THIS method —
 * deliberately a static utility, not a bean, so the OTP limiter's constructor signature (and
 * its test) stays byte-identical while the script lives in exactly one place.
 */
public final class FixedWindowCounter {

    private static final DefaultRedisScript<Long> INCR_WITH_TTL =
            new DefaultRedisScript<>(
                    "local count = redis.call('INCR', KEYS[1]) " +
                            "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end " +
                            "return count",
                    Long.class);

    private FixedWindowCounter() {
    }

    /** Returns the counter value after this increment (0 only if Redis returned null). */
    public static long increment(StringRedisTemplate redis, String key, Duration window) {
        Long count = redis.execute(INCR_WITH_TTL, List.of(key), String.valueOf(window.toMillis()));
        return count == null ? 0L : count;
    }
}
