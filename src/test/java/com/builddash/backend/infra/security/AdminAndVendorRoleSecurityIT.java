package com.builddash.backend.infra.security;

import com.builddash.backend.domain.port.TokenIssuer;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAndVendorRoleSecurityIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenIssuer tokenIssuer;

    private String adminToken;
    private String vendorToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        adminToken = "Bearer " + tokenIssuer.issueAccessToken(userId, deviceId, List.of("ADMIN")).token();
        vendorToken = "Bearer " + tokenIssuer.issueAccessToken(userId, deviceId, List.of("VENDOR")).token();
        userToken = "Bearer " + tokenIssuer.issueAccessToken(userId, deviceId, List.of("USER")).token();
    }

    @Test
    void adminEndpoints_requireAdminRole() throws Exception {
        mockMvc.perform(get("/admin/vendors"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/admin/vendors")
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/vendors")
                        .header(HttpHeaders.AUTHORIZATION, vendorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/vendors")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void returnStaffEndpoints_allowVendorAndAdmin_rejectUserAndAnonymous() throws Exception {
        UUID randomReturnId = UUID.randomUUID();

        mockMvc.perform(post("/returns/{id}/approve", randomReturnId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/returns/{id}/approve", randomReturnId)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden());

        // For VENDOR and ADMIN, it clears the filter gate (may return 404 because random returnId doesn't exist)
        mockMvc.perform(post("/returns/{id}/approve", randomReturnId)
                        .header(HttpHeaders.AUTHORIZATION, vendorToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/returns/{id}/approve", randomReturnId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void supportTicketEscalate_allowVendorAndAdmin_rejectUserAndAnonymous() throws Exception {
        UUID randomTicketId = UUID.randomUUID();

        mockMvc.perform(post("/support/tickets/{id}/escalate", randomTicketId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/support/tickets/{id}/escalate", randomTicketId)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden());

        // For VENDOR and ADMIN, clears security gate and hits controller (returns 404 for non-existent ticket)
        mockMvc.perform(post("/support/tickets/{id}/escalate", randomTicketId)
                        .header(HttpHeaders.AUTHORIZATION, vendorToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/support/tickets/{id}/escalate", randomTicketId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
    }
}
