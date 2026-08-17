package com.builddash.backend.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "security.jwt")
@Getter
@Setter
public class JwtProperties {

    private String secret;
    private String issuer;
    private long accessTokenTtlMinutes;
    private long refreshTokenTtlDays;
    private long guestTokenTtlHours;
}
