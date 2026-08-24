package com.builddash.backend.infra.config;

import com.builddash.backend.domain.port.OtpConfig;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "otp")
@Getter
@Setter
public class OtpProperties implements OtpConfig {

    private int length;
    private long ttlSeconds;
    private int maxAttempts;
    private long sendCooldownSeconds;
    private long rateLimitPerHour;
}
