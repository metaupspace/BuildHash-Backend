package com.builddash.backend.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.builddash.backend.domain.model.GoogleUserInfo;
import com.builddash.backend.domain.port.GoogleIdentityGateway;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends AbstractIntegrationTest {

    @MockBean
    private GoogleIdentityGateway googleTokenVerifier;

    @Test
    void sendOtp_happyPath_returnsMessageAndDispatchesOtp() throws Exception {
        String phone = "+911111100001";

        mockMvc.perform(post("/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("OTP sent"));

        assertThat(smsGateway.lastOtpFor(phone)).matches("\\d{6}");
    }

    @Test
    void sendOtp_invalidPhoneFormat_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendOtp_exceedingHourlyRateLimit_returnsTooManyRequests() throws Exception {
        String phone = "+911111100002";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/otp/send")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"" + phone + "\"}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void verifyOtp_happyPath_issuesTokensAndCreatesUser() throws Exception {
        JsonNode tokens = loginViaOtp("+911111100003", "JUnit-Device-A");

        assertThat(tokens.get("accessToken").asText()).isNotBlank();
        assertThat(tokens.get("refreshToken").asText()).isNotBlank();
        assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");
    }

    @Test
    void verifyOtp_noOtpRequested_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+911111100099\",\"otp\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
    }

    @Test
    void verifyOtp_wrongCodeThreeTimesThenLocksOut() throws Exception {
        String phone = "+911111100004";
        mockMvc.perform(post("/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isOk());

        String correctOtp = smsGateway.lastOtpFor(phone);
        String wrongOtp = "000000".equals(correctOtp) ? "111111" : "000000";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/otp/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\"" + phone + "\",\"otp\":\"" + wrongOtp + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("OTP_INCORRECT"));
        }

        // 4th attempt is locked out even with the correct OTP.
        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"otp\":\"" + correctOtp + "\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("OTP_LOCKED"));
    }

    @Test
    void googleSignIn_happyPath_issuesTokens() throws Exception {
        when(googleTokenVerifier.verify(anyString()))
                .thenReturn(new GoogleUserInfo("google-subject-1", "buyer@example.com", "Test Buyer"));

        mockMvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"fake-valid-token\",\"deviceFingerprint\":\"JUnit-Google-Device\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void googleSignIn_invalidToken_returnsUnauthorized() throws Exception {
        when(googleTokenVerifier.verify(anyString()))
                .thenThrow(new com.builddash.backend.domain.exception.UnauthorizedException(
                        "INVALID_GOOGLE_TOKEN", "Google ID token is invalid or expired"));

        mockMvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"garbage\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_GOOGLE_TOKEN"));
    }

    @Test
    void guestSession_happyPath_issuesNonRefreshableToken() throws Exception {
        mockMvc.perform(post("/auth/guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void refresh_happyPath_rotatesTokenPair() throws Exception {
        JsonNode tokens = loginViaOtp("+911111100005", "JUnit-Device-B");
        String oldRefreshToken = tokens.get("refreshToken").asText();

        MvcResult result = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode rotated = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(rotated.get("refreshToken").asText()).isNotEqualTo(oldRefreshToken);
    }

    @Test
    void refresh_reusingRotatedToken_returnsUnauthorizedAndRevokesDevice() throws Exception {
        JsonNode tokens = loginViaOtp("+911111100006", "JUnit-Device-C");
        String oldRefreshToken = tokens.get("refreshToken").asText();

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefreshToken + "\"}"))
                .andExpect(status().isOk());

        // Reusing the now-stale (already-rotated) refresh token is treated as compromise.
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSE_DETECTED"));
    }

    @Test
    void refresh_withGarbageToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"not-a-real-jwt\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
