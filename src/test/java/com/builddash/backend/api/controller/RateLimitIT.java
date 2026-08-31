package com.builddash.backend.api.controller;

import com.builddash.backend.domain.port.GoogleIdentityGateway;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Real filter chain + embedded Redis against the SHIPPED rules, at real (low) limits via
 * @DynamicPropertySource — which outranks the test-wide raised limits AbstractIntegrationTest
 * sets as system properties, so this class gets its own Spring context and exercises genuine
 * configuration, not the raised test defaults. Every test uses its own X-Forwarded-For IP so
 * budgets never bleed across tests sharing the context's Redis.
 */
class RateLimitIT extends AbstractIntegrationTest {

    @MockBean
    private GoogleIdentityGateway googleTokenVerifier;

    @DynamicPropertySource
    static void realLimits(DynamicPropertyRegistry registry) {
        registry.add("security.rate-limit.rules.search.limit", () -> "3");
        registry.add("security.rate-limit.rules.search.window", () -> "5s");
        registry.add("security.rate-limit.rules.google.limit", () -> "2");
        registry.add("security.rate-limit.rules.google.window", () -> "5s");
    }

    @BeforeEach
    void stubGoogleToFail() {
        // Deterministic 401 from the controller — the point is the FILTER's 429 before it.
        when(googleTokenVerifier.verify(anyString()))
                .thenThrow(new com.builddash.backend.domain.exception.UnauthorizedException(
                        "INVALID_GOOGLE_TOKEN", "Google ID token is invalid or expired"));
    }

    private int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    @Test
    void search_threeAllowedThenFourthIs429() throws Exception {
        for (int i = 0; i < 3; i++) {
            MvcResult result = mockMvc.perform(get("/search?q=brick").header("X-Forwarded-For", "198.51.100.1"))
                    .andReturn();
            assertThat(status(result)).isNotEqualTo(429);
        }
        MvcResult blocked = mockMvc.perform(get("/search?q=brick").header("X-Forwarded-For", "198.51.100.1"))
                .andReturn();
        assertThat(status(blocked)).isEqualTo(429);
        assertThat(blocked.getResponse().getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void budgetsIndependent_exhaustingGoogleLeavesSearchUntouched() throws Exception {
        String ip = "198.51.100.2";
        for (int i = 0; i < 2; i++) {
            MvcResult result = mockMvc.perform(post("/auth/google").contentType(APPLICATION_JSON)
                            .content("{\"idToken\":\"any\",\"deviceFingerprint\":\"d\"}")
                            .header("X-Forwarded-For", ip))
                    .andReturn();
            assertThat(status(result)).isEqualTo(401);   // controller reached, rule allowed
        }
        MvcResult blocked = mockMvc.perform(post("/auth/google").contentType(APPLICATION_JSON)
                        .content("{\"idToken\":\"any\",\"deviceFingerprint\":\"d\"}")
                        .header("X-Forwarded-For", ip))
                .andReturn();
        assertThat(status(blocked)).isEqualTo(429);

        // google's budget is gone; search (separate bucket key) is untouched.
        for (int i = 0; i < 3; i++) {
            MvcResult result = mockMvc.perform(get("/search?q=brick").header("X-Forwarded-For", ip))
                    .andReturn();
            assertThat(status(result)).isNotEqualTo(429);
        }
    }

    @Test
    void perIpSeparation_distinctForwardedIpsHaveDistinctBudgets() throws Exception {
        String exhaustedIp = "198.51.100.3";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/search?q=brick").header("X-Forwarded-For", exhaustedIp)).andReturn();
        }
        MvcResult blocked = mockMvc.perform(get("/search?q=brick").header("X-Forwarded-For", exhaustedIp))
                .andReturn();
        assertThat(status(blocked)).isEqualTo(429);

        MvcResult otherIp = mockMvc.perform(get("/search?q=brick").header("X-Forwarded-For", "198.51.100.4"))
                .andReturn();
        assertThat(status(otherIp)).isNotEqualTo(429);
    }

    @Test
    void windowExpiry_realTimePassageRestoresBudget() throws Exception {
        String ip = "198.51.100.5";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/search?q=brick").header("X-Forwarded-For", ip)).andReturn();
        }
        MvcResult blocked = mockMvc.perform(get("/search?q=brick").header("X-Forwarded-For", ip))
                .andReturn();
        assertThat(status(blocked)).isEqualTo(429);

        // Real time passage against the real Redis TTL (window 5s) — no mocked clock.
        Thread.sleep(Duration.ofSeconds(5).plusMillis(500).toMillis());

        MvcResult after = mockMvc.perform(get("/search?q=brick").header("X-Forwarded-For", ip))
                .andReturn();
        assertThat(status(after)).isNotEqualTo(429);
    }
}
