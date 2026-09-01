package com.builddash.backend.infra.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "security.jwt")
@Getter
@Setter
public class JwtProperties {

    /** No default and no blank: missing secret fails startup (H0.1), like PII_MASTER_KEY. */
    @NotBlank
    private String secret;
    private String issuer;
    private long accessTokenTtlMinutes;
    private long refreshTokenTtlDays;
    private long guestTokenTtlHours;
}
