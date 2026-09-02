package com.builddash.backend.api.controller;

import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.CompanyStatus;
import com.builddash.backend.domain.enums.StatementEmailStatus;
import com.builddash.backend.domain.enums.StatementStatus;
import com.builddash.backend.domain.model.Company;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.model.Statement;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.domain.port.CompanyRepository;
import com.builddash.backend.domain.port.StatementRepository;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatementControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyMemberRepository memberRepository;

    @Autowired
    private StatementRepository statementRepository;

    private String memberToken;
    private String nonMemberToken;
    private UUID companyId;
    private Statement statement;

    @BeforeEach
    void setUp() throws Exception {
        String phoneA = "+919900110001";
        JsonNode tokensA = loginViaOtp(phoneA, "Device-Statement-Owner");
        memberToken = "Bearer " + tokensA.get("accessToken").asText();
        UUID userAId = userRepository.findByPhone(phoneA).orElseThrow().getId();

        String phoneB = "+919900110002";
        JsonNode tokensB = loginViaOtp(phoneB, "Device-Statement-Stranger");
        nonMemberToken = "Bearer " + tokensB.get("accessToken").asText();

        Company savedCompany = companyRepository.save(new Company(
                UUID.randomUUID(), "Acme Infra", "GST123", "acme@example.com", "Asia/Kolkata",
                CompanyStatus.ACTIVE, Instant.now(), Instant.now()
        ));
        companyId = savedCompany.id();

        memberRepository.save(new CompanyMember(
                UUID.randomUUID(), companyId, userAId, CompanyRole.OWNER,
                Instant.now(), Instant.now()
        ));

        UUID statementId = UUID.randomUUID();
        statement = statementRepository.save(new Statement(
                statementId, companyId,
                Instant.parse("2026-07-31T18:30:00Z"), Instant.parse("2026-08-31T18:30:00Z"), "2026-08",
                StatementStatus.READY, 1, "STM-2627-000001",
                "statements/" + companyId + "/" + statementId + ".pdf",
                "statements/" + companyId + "/" + statementId + ".xlsx",
                1000L, 2000L, Instant.now(), 1,
                StatementEmailStatus.SENT, Instant.now(), 1,
                5, new BigDecimal("10000.00"), new BigDecimal("1800.00"),
                new BigDecimal("11800.00"), BigDecimal.ZERO, new BigDecimal("11800.00"),
                List.of(), Instant.now(), Instant.now()
        ));
    }

    @Test
    void listStatements_authorizedMember_returns200() throws Exception {
        mockMvc.perform(get("/companies/{companyId}/statements", companyId)
                        .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].statementNumber").value("STM-2627-000001"))
                .andExpect(jsonPath("$[0].status").value("READY"))
                .andExpect(jsonPath("$[0].pdfUrl").exists());
    }

    @Test
    void listStatements_nonMember_returns404() throws Exception {
        mockMvc.perform(get("/companies/{companyId}/statements", companyId)
                        .header(HttpHeaders.AUTHORIZATION, nonMemberToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void listStatements_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/companies/{companyId}/statements", companyId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getStatement_authorizedMember_returns200() throws Exception {
        mockMvc.perform(get("/statements/{id}", statement.id())
                        .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(statement.id().toString()))
                .andExpect(jsonPath("$.statementNumber").value("STM-2627-000001"))
                .andExpect(jsonPath("$.periodKey").value("2026-08"));
    }

    @Test
    void getStatement_nonMember_returns404() throws Exception {
        mockMvc.perform(get("/statements/{id}", statement.id())
                        .header(HttpHeaders.AUTHORIZATION, nonMemberToken))
                .andExpect(status().isNotFound());
    }
}
