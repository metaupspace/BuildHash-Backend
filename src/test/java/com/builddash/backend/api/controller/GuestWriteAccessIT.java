package com.builddash.backend.api.controller;

import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuestWriteAccessIT extends AbstractIntegrationTest {

    private String guestToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/guest"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.accessToken");
    }

    @Test
    void threeWayAccessControl_enforcesBoundariesCorrectly() throws Exception {
        String token = guestToken();

        // 1. Guest-allowed mutation: PUT /cart/items -> 400 Bad Request (missing body), proves it cleared security
        mockMvc.perform(put("/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // 2. Guest-blocked mutation: POST /orders -> 403 Forbidden
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        // 3. Public endpoint: POST /auth/guest without token -> 200 OK
        mockMvc.perform(post("/auth/guest"))
                .andExpect(status().isOk());
                
        // 4. Public endpoint with guest token: POST /auth/refresh -> 400
        mockMvc.perform(post("/auth/refresh")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void guestToken_canStillReadPublicCatalog() throws Exception {
        String token = guestToken();

        mockMvc.perform(get("/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
