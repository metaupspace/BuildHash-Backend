package com.builddash.backend.infra.security;

import com.builddash.backend.api.dto.ApiError;
import com.builddash.backend.domain.port.RateLimiter;
import com.builddash.backend.infra.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies the yaml-configured rate-limit rules (PLAN_PHASE8 decision 7) before anything else
 * runs — highest precedence, ahead of the security chain, so public endpoints throttle before
 * JWT work. Violations self-render 429 (ApiError, code RATE_LIMITED): a servlet filter runs
 * before DispatcherServlet, where @RestControllerAdvice cannot intercept throws — the
 * GlobalExceptionHandler 429 mapping stays for service-layer throws (e.g. OTP's limiter).
 * OPTIONS is exempt so CORS preflights never burn a budget.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    /** Rule patterns are immutable after boot; compile once per rule. */
    private final Map<String, org.springframework.web.util.pattern.PathPattern> compiledPatterns = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        for (Map.Entry<String, RateLimitProperties.Rule> entry : properties.getRules().entrySet()) {
            RateLimitProperties.Rule rule = entry.getValue();
            if (rule == null || !matches(rule, request)) {
                continue;
            }
            String clientIp = ClientIpResolver.resolve(request);
            boolean allowed = rateLimiter.allow(entry.getKey(), clientIp, rule.getLimit(), rule.getWindow());
            if (!allowed) {
                log.info("Rate limit hit: rule={} ip={} {} {}", entry.getKey(), clientIp, request.getMethod(), request.getRequestURI());
                renderTooManyRequests(request, response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean matches(RateLimitProperties.Rule rule, HttpServletRequest request) {
        var pattern = compiledPatterns.computeIfAbsent(rule.getPath(),
                p -> new org.springframework.web.util.pattern.PathPatternParser().parse(p));
        if (!pattern.matches(ServletRequestPathUtils.parseAndCache(request))) {
            return false;
        }
        return rule.getMethod() == null || rule.getMethod().isBlank()
                || rule.getMethod().equalsIgnoreCase(request.getMethod());
    }

    private void renderTooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ApiError body = new ApiError(Instant.now(), HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(), "RATE_LIMITED",
                "Too many requests, try again later", request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
