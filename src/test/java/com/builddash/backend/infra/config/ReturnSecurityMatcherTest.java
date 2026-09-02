package com.builddash.backend.infra.config;

import com.builddash.backend.domain.port.TokenIssuer;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReturnSecurityMatcherTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String userToken;
    private String guestToken;
    private String vendorToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        UUID userId = UUID.randomUUID();
        UUID guestId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now()) ON CONFLICT DO NOTHING", userId);
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now()) ON CONFLICT DO NOTHING", guestId);

        userToken = "Bearer " + tokenIssuer.issueAccessToken(userId, UUID.randomUUID(), List.of("USER")).token();
        guestToken = "Bearer " + tokenIssuer.issueGuestToken(guestId).token();
        vendorToken = "Bearer " + tokenIssuer.issueAccessToken(UUID.randomUUID(), UUID.randomUUID(), List.of("VENDOR")).token();
        adminToken = "Bearer " + tokenIssuer.issueAccessToken(UUID.randomUUID(), UUID.randomUUID(), List.of("ADMIN")).token();
    }

    @Test
    void postOrderReturn_securityMatchers_enforceUserRole() throws Exception {
        UUID orderId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("photos", "a.jpg", "image/jpeg", new byte[]{1});
        MockMultipartFile json = new MockMultipartFile("request", "", "application/json", "{}".getBytes());

        // Unauthenticated -> 401
        mockMvc.perform(multipart("/orders/{id}/return", orderId).file(file).file(json))
                .andExpect(status().isUnauthorized());

        // GUEST -> 403
        mockMvc.perform(multipart("/orders/{id}/return", orderId).file(file).file(json)
                        .header(HttpHeaders.AUTHORIZATION, guestToken))
                .andExpect(status().isForbidden());

        // VENDOR (without USER role) -> 403
        mockMvc.perform(multipart("/orders/{id}/return", orderId).file(file).file(json)
                        .header(HttpHeaders.AUTHORIZATION, vendorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getReturn_securityMatchers_allowUserVendorAdmin_rejectGuest() throws Exception {
        UUID returnId = UUID.randomUUID();

        // Unauthenticated -> 401
        mockMvc.perform(get("/returns/{id}", returnId))
                .andExpect(status().isUnauthorized());

        // GUEST -> 403
        mockMvc.perform(get("/returns/{id}", returnId)
                        .header(HttpHeaders.AUTHORIZATION, guestToken))
                .andExpect(status().isForbidden());

        // USER, VENDOR, ADMIN pass security filter (returns 404 since returnId does not exist, not 401/403)
        mockMvc.perform(get("/returns/{id}", returnId)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/returns/{id}", returnId)
                        .header(HttpHeaders.AUTHORIZATION, vendorToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/returns/{id}", returnId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void postReturnApprove_securityMatchers_allowOnlyVendorAndAdmin() throws Exception {
        UUID returnId = UUID.randomUUID();

        // Unauthenticated -> 401
        mockMvc.perform(post("/returns/{id}/approve", returnId))
                .andExpect(status().isUnauthorized());

        // GUEST -> 403
        mockMvc.perform(post("/returns/{id}/approve", returnId)
                        .header(HttpHeaders.AUTHORIZATION, guestToken))
                .andExpect(status().isForbidden());

        // USER -> 403
        mockMvc.perform(post("/returns/{id}/approve", returnId)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden());

        // VENDOR -> passes security (404 for non-existent returnId)
        mockMvc.perform(post("/returns/{id}/approve", returnId)
                        .header(HttpHeaders.AUTHORIZATION, vendorToken))
                .andExpect(status().isNotFound());

        // ADMIN -> passes security (404 for non-existent returnId)
        mockMvc.perform(post("/returns/{id}/approve", returnId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void postReturnSchedulePickup_securityMatchers_allowOnlyVendorAndAdmin() throws Exception {
        UUID returnId = UUID.randomUUID();

        // Unauthenticated -> 401
        mockMvc.perform(post("/returns/{id}/schedule-pickup", returnId))
                .andExpect(status().isUnauthorized());

        // GUEST -> 403
        mockMvc.perform(post("/returns/{id}/schedule-pickup", returnId)
                        .header(HttpHeaders.AUTHORIZATION, guestToken))
                .andExpect(status().isForbidden());

        // USER -> 403
        mockMvc.perform(post("/returns/{id}/schedule-pickup", returnId)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden());

        // VENDOR -> passes security (404 for non-existent returnId)
        mockMvc.perform(post("/returns/{id}/schedule-pickup", returnId)
                        .header(HttpHeaders.AUTHORIZATION, vendorToken))
                .andExpect(status().isNotFound());

        // ADMIN -> passes security (404 for non-existent returnId)
        mockMvc.perform(post("/returns/{id}/schedule-pickup", returnId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void postReturnPickup_securityMatchers_allowOnlyVendorAndAdmin() throws Exception {
        UUID returnId = UUID.randomUUID();

        // Unauthenticated -> 401
        mockMvc.perform(post("/returns/{id}/pickup", returnId))
                .andExpect(status().isUnauthorized());

        // GUEST -> 403
        mockMvc.perform(post("/returns/{id}/pickup", returnId)
                        .header(HttpHeaders.AUTHORIZATION, guestToken))
                .andExpect(status().isForbidden());

        // USER -> 403
        mockMvc.perform(post("/returns/{id}/pickup", returnId)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden());

        // VENDOR -> passes security (404 for non-existent returnId)
        mockMvc.perform(post("/returns/{id}/pickup", returnId)
                        .header(HttpHeaders.AUTHORIZATION, vendorToken))
                .andExpect(status().isNotFound());

        // ADMIN -> passes security (404 for non-existent returnId)
        mockMvc.perform(post("/returns/{id}/pickup", returnId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void postReturnReject_securityMatchers_allowOnlyVendorAndAdmin() throws Exception {
        UUID returnId = UUID.randomUUID();

        // Unauthenticated -> 401
        mockMvc.perform(post("/returns/{id}/reject", returnId))
                .andExpect(status().isUnauthorized());

        // GUEST -> 403
        mockMvc.perform(post("/returns/{id}/reject", returnId)
                        .header(HttpHeaders.AUTHORIZATION, guestToken))
                .andExpect(status().isForbidden());

        // USER -> 403
        mockMvc.perform(post("/returns/{id}/reject", returnId)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden());

        // VENDOR -> passes security (404 for non-existent returnId)
        mockMvc.perform(post("/returns/{id}/reject", returnId)
                        .header(HttpHeaders.AUTHORIZATION, vendorToken))
                .andExpect(status().isNotFound());

        // ADMIN -> passes security (404 for non-existent returnId)
        mockMvc.perform(post("/returns/{id}/reject", returnId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void postReturnQcPass_securityMatchers_allowOnlyVendorAndAdmin() throws Exception {
        UUID returnId = UUID.randomUUID();

        // Unauthenticated -> 401
        mockMvc.perform(post("/returns/{id}/qc-pass", returnId))
                .andExpect(status().isUnauthorized());

        // GUEST -> 403
        mockMvc.perform(post("/returns/{id}/qc-pass", returnId)
                        .header(HttpHeaders.AUTHORIZATION, guestToken))
                .andExpect(status().isForbidden());

        // USER -> 403
        mockMvc.perform(post("/returns/{id}/qc-pass", returnId)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden());

        // VENDOR -> passes security (404 for non-existent returnId)
        mockMvc.perform(post("/returns/{id}/qc-pass", returnId)
                        .header(HttpHeaders.AUTHORIZATION, vendorToken))
                .andExpect(status().isNotFound());

        // ADMIN -> passes security (404 for non-existent returnId)
        mockMvc.perform(post("/returns/{id}/qc-pass", returnId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrderInvoice_securityMatchers_allowOnlyUser() throws Exception {
        UUID orderId = UUID.randomUUID();

        // Unauthenticated -> 401
        mockMvc.perform(get("/orders/{id}/invoice", orderId))
                .andExpect(status().isUnauthorized());

        // GUEST -> 403
        mockMvc.perform(get("/orders/{id}/invoice", orderId)
                        .header(HttpHeaders.AUTHORIZATION, guestToken))
                .andExpect(status().isForbidden());

        // USER -> passes security filter (404 for non-existent orderId)
        mockMvc.perform(get("/orders/{id}/invoice", orderId)
                        .header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void refundWebhook_isPermitAll() throws Exception {
        // Unauthenticated request reaches controller (returns 401 from signature verification, not Spring Security filter)
        mockMvc.perform(post("/api/webhooks/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"returnId\":\"" + UUID.randomUUID() + "\",\"gatewayRefundId\":\"gw1\",\"status\":\"SUCCESS\",\"signature\":\"invalidsig\"}"))
                .andExpect(status().isUnauthorized());
    }
}
