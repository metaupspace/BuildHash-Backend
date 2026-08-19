package com.builddash.backend.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginHistoryControllerIT extends AbstractIntegrationTest {

    @Test
    void getLoginHistory_afterOtpLogin_containsOtpEvent() throws Exception {
        JsonNode tokens = loginViaOtp("+911111400001", "JUnit-Device");
        String accessToken = tokens.get("accessToken").asText();

        mockMvc.perform(get("/users/me/login-history").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("OTP"));
    }

    @Test
    void getLoginHistory_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/users/me/login-history"))
                .andExpect(status().isUnauthorized());
    }
}
