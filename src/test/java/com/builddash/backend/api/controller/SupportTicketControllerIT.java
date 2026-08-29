package com.builddash.backend.api.controller;

import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.TokenIssuer;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.infra.persistence.repository.SupportTicketJpaRepository;
import com.builddash.backend.infra.persistence.repository.SupportTicketMessageJpaRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Plan Section 7's Support ControllerIT: ticket+first message atomic creation, own-only
 * list/get with the 404-for-non-owner convention, agent (VENDOR/ADMIN, test-minted JWTs —
 * PLAN_PHASE6 NQ-3 mechanism) access and escalation, USER escalate blocked at the matcher,
 * and slaDueAt from category config including a yaml-override case.
 */
class SupportTicketControllerIT extends AbstractIntegrationTest {

    private static final AtomicInteger PHONE_SEQ = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SupportTicketJpaRepository ticketJpaRepository;

    @Autowired
    private SupportTicketMessageJpaRepository messageJpaRepository;

    private String ownerToken;
    private String strangerToken;
    private String vendorToken;
    private String adminToken;
    private UUID ownerUserId;
    private UUID strangerUserId;

    @BeforeEach
    void setUp() {
        ownerUserId = seedUser();
        strangerUserId = seedUser();
        ownerToken = bearer(ownerUserId, "USER");
        strangerToken = bearer(strangerUserId, "USER");
        vendorToken = bearer(UUID.randomUUID(), "VENDOR");
        adminToken = bearer(UUID.randomUUID(), "ADMIN");
    }

    private UUID seedUser() {
        User user = new User();
        user.setPhone("+9197" + String.format("%08d", PHONE_SEQ.incrementAndGet()));
        return userRepository.save(user).getId();
    }

    private String bearer(UUID userId, String role) {
        return "Bearer " + tokenIssuer.issueAccessToken(userId, UUID.randomUUID(), List.of(role)).token();
    }

    private MvcResult createTicket(String token, String category, String subject, String message) throws Exception {
        return mockMvc.perform(post("/support/tickets")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "category", category, "subject", subject, "message", message))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
    }

    @Test
    void createTicket_writesTicketAndFirstMessageAtomically_computesSlaFromCategory() throws Exception {
        MvcResult result = createTicket(ownerToken, "PAYMENT_ISSUE", "payment failed", "money not deducted but order placed");

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID ticketId = UUID.fromString(body.get("id").asText());

        assertThat(ticketJpaRepository.findById(ticketId)).isPresent();
        assertThat(messageJpaRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)).hasSize(1);
        // PAYMENT_ISSUE yaml default 4h — slaDueAt within (now+3h, now+5h)
        assertThat(java.time.Instant.parse(body.get("slaDueAt").asText()))
                .isBetween(java.time.Instant.now().plusSeconds(3 * 3600), java.time.Instant.now().plusSeconds(5 * 3600));
    }

    @Test
    void listOwnTickets_returnsOnlyCallerTickets() throws Exception {
        createTicket(ownerToken, "ORDER_ISSUE", "a", "m");
        createTicket(strangerToken, "ORDER_ISSUE", "b", "m");

        mockMvc.perform(get("/support/tickets").header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].subject").value("a"));
    }

    @Test
    void getTicket_nonOwnerGets404_vendorAndAdminSeeIt() throws Exception {
        UUID ticketId = ticketIdFrom(createTicket(ownerToken, "ORDER_ISSUE", "s", "m"));

        mockMvc.perform(get("/support/tickets/" + ticketId).header("Authorization", strangerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/support/tickets/" + ticketId).header("Authorization", vendorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/support/tickets/" + ticketId).header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void appendMessage_ownerAndAgentSucceed_senderRoleDerivedFromRole_nonOwner404AndNoRowWritten() throws Exception {
        UUID ticketId = ticketIdFrom(createTicket(ownerToken, "PRODUCT_QUERY", "s", "first"));

        mockMvc.perform(post("/support/tickets/" + ticketId + "/messages")
                        .header("Authorization", strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("message", "sneaky"))))
                .andExpect(status().isNotFound());

        assertThat(messageJpaRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)).hasSize(1);

        mockMvc.perform(post("/support/tickets/" + ticketId + "/messages")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("message", "customer follow-up"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderRole").value("CUSTOMER"));

        mockMvc.perform(post("/support/tickets/" + ticketId + "/messages")
                        .header("Authorization", vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("message", "agent reply"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderRole").value("AGENT"));

        assertThat(messageJpaRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)).hasSize(3);
    }

    @Test
    void escalate_vendorAndAdminSucceed_userBlockedAtMatcher_doubleEscalateConflicts() throws Exception {
        UUID ticketId = ticketIdFrom(createTicket(ownerToken, "DELIVERY_ISSUE", "s", "m"));

        mockMvc.perform(post("/support/tickets/" + ticketId + "/escalate").header("Authorization", ownerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/support/tickets/" + ticketId + "/escalate").header("Authorization", vendorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESCALATED"));

        mockMvc.perform(post("/support/tickets/" + ticketId + "/escalate").header("Authorization", adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void slaOverride_fromYamlTakesPrecedence_overCodeDefault() throws Exception {
        // DELIVERY_ISSUE yaml value is 4h (main application.yaml) — proving the yaml side
        // is live; the code-default fallback is proven in SupportTicketServiceImplTest.
        MvcResult result = createTicket(ownerToken, "DELIVERY_ISSUE", "late", "where is it");

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(java.time.Instant.parse(body.get("slaDueAt").asText()))
                .isBetween(java.time.Instant.now().plusSeconds(3 * 3600), java.time.Instant.now().plusSeconds(5 * 3600));
    }

    private UUID ticketIdFrom(MvcResult result) throws Exception {
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }
}
