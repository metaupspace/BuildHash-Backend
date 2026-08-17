package com.builddash.backend.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerIT extends AbstractIntegrationTest {

    @Test
    void getMe_happyPath_returnsProfile() throws Exception {
        String phone = "+911111200001";
        JsonNode tokens = loginViaOtp(phone, "JUnit-Device");
        String accessToken = tokens.get("accessToken").asText();

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(phone));
    }

    @Test
    void getMe_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateMe_validGstNumber_setsStatusPending() throws Exception {
        JsonNode tokens = loginViaOtp("+911111200002", "JUnit-Device");
        String accessToken = tokens.get("accessToken").asText();

        mockMvc.perform(put("/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ramesh Sharma\",\"businessName\":\"Sharma Traders\",\"gstNumber\":\"27AAAPZ1234C1Z5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ramesh Sharma"))
                .andExpect(jsonPath("$.businessName").value("Sharma Traders"))
                .andExpect(jsonPath("$.gstNumber").value("27AAAPZ1234C1Z5"))
                .andExpect(jsonPath("$.gstinStatus").value("PENDING"));
    }

    @Test
    void updateMe_invalidGstNumberFormat_returnsBadRequest() throws Exception {
        JsonNode tokens = loginViaOtp("+911111200003", "JUnit-Device");
        String accessToken = tokens.get("accessToken").asText();

        mockMvc.perform(put("/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gstNumber\":\"NOT-A-GSTIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
