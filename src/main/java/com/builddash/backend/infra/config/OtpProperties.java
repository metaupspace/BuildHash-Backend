package com.builddash.backend.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "otp")
@Getter
@Setter
public class OtpProperties {

    private int length;
    private long ttlSeconds;
    private int maxAttempts;
    private long sendCooldownSeconds;
    private long rateLimitPerHour;
}
