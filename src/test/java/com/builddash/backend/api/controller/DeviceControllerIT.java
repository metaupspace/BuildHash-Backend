package com.builddash.backend.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeviceControllerIT extends AbstractIntegrationTest {

    @Test
    void listDevices_returnsTheDeviceJustLoggedInWith() throws Exception {
        JsonNode tokens = loginViaOtp("+911111300001", "JUnit-Listed-Device");
        String accessToken = tokens.get("accessToken").asText();

        mockMvc.perform(get("/users/me/devices").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deviceFingerprint").value("JUnit-Listed-Device"));
    }

    @Test
    void revokeDevice_belongingToAnotherUser_returnsNotFound() throws Exception {
        JsonNode user1Tokens = loginViaOtp("+911111300002", "User1-Device");
        String user1Access = user1Tokens.get("accessToken").asText();

        JsonNode user2Tokens = loginViaOtp("+911111300003", "User2-Device");
        String user2Access = user2Tokens.get("accessToken").asText();

        MvcResult listResult = mockMvc.perform(get("/users/me/devices")
                        .header("Authorization", "Bearer " + user1Access))
                .andExpect(status().isOk())
                .andReturn();
        String user1DeviceId = objectMapper.readTree(listResult.getResponse().getContentAsString()).get(0).get("id").asText();

        mockMvc.perform(delete("/users/me/devices/" + user1DeviceId)
                        .header("Authorization", "Bearer " + user2Access))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokeDevice_ownDevice_thenRefreshTokenNoLongerWorks() throws Exception {
        JsonNode tokens = loginViaOtp("+911111300004", "JUnit-Revoke-Device");
        String accessToken = tokens.get("accessToken").asText();
        String refreshToken = tokens.get("refreshToken").asText();

        MvcResult listResult = mockMvc.perform(get("/users/me/devices")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        String deviceId = objectMapper.readTree(listResult.getResponse().getContentAsString()).get(0).get("id").asText();

        mockMvc.perform(delete("/users/me/devices/" + deviceId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAllDevices_reissuesFreshSessionAndInvalidatesOldRefreshToken() throws Exception {
        JsonNode tokens = loginViaOtp("+911111300005", "JUnit-LogoutAll-Device");
        String accessToken = tokens.get("accessToken").asText();
        String oldRefreshToken = tokens.get("refreshToken").asText();

        MvcResult logoutAllResult = mockMvc.perform(post("/users/me/logout-all-devices")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode freshTokens = objectMapper.readTree(logoutAllResult.getResponse().getContentAsString());
        assertThat(freshTokens.get("refreshToken").asText()).isNotEqualTo(oldRefreshToken);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + freshTokens.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isOk());
    }
}
