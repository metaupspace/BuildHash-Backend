package com.builddash.backend.infra.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI builddashOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("BuildDash Backend API")
                        .version("0.0.1")
                        .description("Phase 0 (Foundations) — Account & Identity APIs: OTP auth, Google sign-in, "
                                + "guest sessions, JWT refresh, user profile, device/session management, login history, "
                                + "and the HSN/GST master data used by later pricing phases."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
