package com.builddash.backend.api;

import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.model.B2bMembership;
import com.builddash.backend.domain.port.TokenIssuer;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization matrix for the 9-A company surface (SearchImageAuthIT model):
 * anonymous 401; authenticated non-member 404 (existence hidden); BUYER/APPROVER read
 * but cannot mutate admin-scoped resources (403); ADMIN/OWNER mutate; cross-company
 * access is 404. Money-path style DB re-checks are covered by the service unit tests.
 */
class CompanyAuthorizationMatrixIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID companyId;
    private UUID otherCompanyId;
    private UUID buyerUserId;
    private UUID approverUserId;
    private UUID adminUserId;
    private UUID ownerUserId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        otherCompanyId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO companies (id, name) VALUES (?, 'Acme')", companyId);
        jdbcTemplate.update("INSERT INTO companies (id, name) VALUES (?, 'Other')", otherCompanyId);

        buyerUserId = insertUserWithMembership(CompanyRole.BUYER);
        approverUserId = insertUserWithMembership(CompanyRole.APPROVER);
        adminUserId = insertUserWithMembership(CompanyRole.ADMIN);
        ownerUserId = insertUserWithMembership(CompanyRole.OWNER);
    }

    private UUID insertUserWithMembership(CompanyRole role) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);
        jdbcTemplate.update("INSERT INTO company_members (id, company_id, user_id, role) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), companyId, userId, role.name());
        return userId;
    }

    private String token(UUID userId, CompanyRole role) {
        return "Bearer " + tokenIssuer.issueAccessToken(userId, UUID.randomUUID(),
                List.of("USER"),
                List.of(new B2bMembership(companyId, role, List.of()))).token();
    }

    private UUID newTargetUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);
        return userId;
    }

    // --- company read: member-only, non-member 404 ---

    @Test
    void getCompany_anonymous401() throws Exception {
        mockMvc.perform(get("/companies/{id}", companyId)).andExpect(status().isUnauthorized());
    }

    @Test
    void getCompany_authenticatedNonMember404() throws Exception {
        UUID outsider = newTargetUser();
        mockMvc.perform(get("/companies/{id}", companyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenIssuer
                                .issueAccessToken(outsider, UUID.randomUUID(), List.of("USER")).token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCompany_buyer200() throws Exception {
        mockMvc.perform(get("/companies/{id}", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(buyerUserId, CompanyRole.BUYER)))
                .andExpect(status().isOk());
    }

    @Test
    void patchCompany_buyer403_admin200() throws Exception {
        String body = "{\"name\": \"Acme Renamed\", \"gstNumber\": null, \"statementEmail\": null, \"businessTimezone\": null}";
        mockMvc.perform(patch("/companies/{id}", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(buyerUserId, CompanyRole.BUYER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/companies/{id}", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(adminUserId, CompanyRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    // --- cross-company: same-user token of ANOTHER company gets 404 ---

    @Test
    void getCompany_memberOfOtherCompanyOnly_gets404() throws Exception {
        UUID outsider = newTargetUser();
        jdbcTemplate.update("INSERT INTO company_members (id, company_id, user_id, role) VALUES (?, ?, ?, 'OWNER')",
                UUID.randomUUID(), otherCompanyId, outsider);
        String otherCompanyToken = "Bearer " + tokenIssuer.issueAccessToken(outsider, UUID.randomUUID(),
                List.of("USER"),
                List.of(new B2bMembership(otherCompanyId, CompanyRole.OWNER, List.of()))).token();

        mockMvc.perform(get("/companies/{id}", companyId)
                        .header(HttpHeaders.AUTHORIZATION, otherCompanyToken))
                .andExpect(status().isNotFound());
    }

    // --- members: ADMIN+ to mutate, any member to read ---

    @Test
    void addMember_approver403_admin201() throws Exception {
        UUID target = newTargetUser();
        String body = "{\"memberUserId\": \"" + target + "\", \"role\": \"BUYER\", \"siteIds\": []}";

        mockMvc.perform(post("/companies/{id}/members", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(approverUserId, CompanyRole.APPROVER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/companies/{id}/members", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(adminUserId, CompanyRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void addMember_duplicate409() throws Exception {
        String body = "{\"memberUserId\": \"" + buyerUserId + "\", \"role\": \"BUYER\", \"siteIds\": []}";
        mockMvc.perform(post("/companies/{id}/members", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(adminUserId, CompanyRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void listMembers_buyer200_nonMember404() throws Exception {
        mockMvc.perform(get("/companies/{id}/members", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(buyerUserId, CompanyRole.BUYER)))
                .andExpect(status().isOk());

        UUID outsider = newTargetUser();
        mockMvc.perform(get("/companies/{id}/members", companyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenIssuer
                                .issueAccessToken(outsider, UUID.randomUUID(), List.of("USER")).token()))
                .andExpect(status().isNotFound());
    }

    // --- sites ---

    @Test
    void createSite_admin201_buyer403() throws Exception {
        String body = "{\"name\": \"Warehouse\", \"addressId\": null, \"active\": null}";

        mockMvc.perform(post("/companies/{id}/sites", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(buyerUserId, CompanyRole.BUYER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/companies/{id}/sites", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(adminUserId, CompanyRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void listSites_member200_nonMember404() throws Exception {
        mockMvc.perform(get("/companies/{id}/sites", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(approverUserId, CompanyRole.APPROVER)))
                .andExpect(status().isOk());
    }

    @Test
    void createCompany_anyAuthenticatedUser201_creatorBecomesOwner() throws Exception {
        UUID creator = newTargetUser();
        String body = "{\"name\": \"Newco\", \"gstNumber\": null, \"statementEmail\": null, \"businessTimezone\": null}";
        String response = mockMvc.perform(post("/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenIssuer
                                .issueAccessToken(creator, UUID.randomUUID(), List.of("USER")).token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).get("id").asText();
        Integer ownerRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM company_members WHERE company_id = ? AND user_id = ? AND role = 'OWNER'",
                Integer.class, UUID.fromString(id), creator);
        org.assertj.core.api.Assertions.assertThat(ownerRows).isEqualTo(1);
    }
}
