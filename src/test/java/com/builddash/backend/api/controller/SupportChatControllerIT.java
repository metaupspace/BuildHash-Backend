package com.builddash.backend.api.controller;

import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.TokenIssuer;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.infra.persistence.repository.SupportTicketJpaRepository;
import com.builddash.backend.infra.persistence.repository.SupportTicketMessageJpaRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Plan Section 7's chat-stub IT: POST /support/chat with the stub's sub-threshold
 * confidence creates an ESCALATED ticket carrying the message as its first entry.
 */
class SupportChatControllerIT extends AbstractIntegrationTest {

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

    private String userToken;
    private UUID userId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setPhone("+9196" + String.format("%08d", PHONE_SEQ.incrementAndGet()));
        userId = userRepository.save(user).getId();
        userToken = "Bearer " + tokenIssuer.issueAccessToken(userId, UUID.randomUUID(), List.of("USER")).token();
    }

    @Test
    void chat_alwaysEscalates_creatingTicketWithMessageAsContext() throws Exception {
        MvcResult result = mockMvc.perform(post("/support/chat")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("message", "my order is late"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("UNKNOWN"))
                .andExpect(jsonPath("$.confidence").value(0.2))
                .andExpect(jsonPath("$.escalated").value(true))
                .andReturn();

        UUID ticketId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .get("ticketId").asText());

        assertThat(ticketJpaRepository.findById(ticketId))
                .hasValueSatisfying(ticket -> assertThat(ticket.getStatus().name()).isEqualTo("ESCALATED"));
        assertThat(messageJpaRepository.findByTicketIdOrderByCreatedAtAsc(ticketId))
                .singleElement()
                .satisfies(message -> assertThat(message.getBody()).isEqualTo("my order is late"));
    }
}
