package com.builddash.backend.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.builddash.backend.infra.security.JwtAuthenticationFilter;
import com.builddash.backend.api.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Instant;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/**",
                                "/api/webhooks/**",
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/categories/**", "/products/**").permitAll()
                        .requestMatchers("/search/**").permitAll()
                        .requestMatchers("/users/**").hasRole("USER")
                        .requestMatchers("/cart/**").hasAnyRole("GUEST", "USER")
                        // Guest tokens may browse (GET) but never write
                        .requestMatchers(HttpMethod.POST, "/**").hasRole("USER")
                        .requestMatchers(HttpMethod.PUT, "/**").hasRole("USER")
                        .requestMatchers(HttpMethod.PATCH, "/**").hasRole("USER")
                        .requestMatchers(HttpMethod.DELETE, "/**").hasRole("USER")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeError(request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have access to this resource"))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                             HttpStatus status, String code, String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message, request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
