package com.builddash.backend.api;

import com.builddash.backend.domain.enums.CompanyPermission;
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
 * Permission-based authorization matrix for the company surface (SearchImageAuthIT
 * model): anonymous 401; non-member 404 (existence hidden); access follows the
 * company's live permission rows, not role names; cross-company 404; app ADMIN gains
 * nothing without membership. Permission grant/revoke live-effect coverage lives in
 * CompanyPermissionMatrixIT.
 */
class CompanyAuthorizationMatrixIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private com.builddash.backend.application.service.CompanyService companyService;

    @Autowired
    private com.builddash.backend.application.service.CompanyMembershipService membershipService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID companyId;
    private UUID ownerUserId;
    private UUID pmUserId;
    private UUID supervisorUserId;
    private UUID accountantUserId;
    private UUID viewerUserId;

    @BeforeEach
    void setUp() {
        // Real creation flow: company + OWNER + all default permission profiles seeded
        ownerUserId = newTargetUser();
        companyId = companyService.create(ownerUserId, "Acme", null, null, null).id();

        pmUserId = insertUserWithMembership(CompanyRole.PROCUREMENT_MANAGER);
        supervisorUserId = insertUserWithMembership(CompanyRole.SITE_SUPERVISOR);
        accountantUserId = insertUserWithMembership(CompanyRole.ACCOUNTANT);
        viewerUserId = insertUserWithMembership(CompanyRole.VIEWER);
    }

    private UUID insertUserWithMembership(CompanyRole role) {
        UUID userId = newTargetUser();
        membershipService.addMember(companyId, ownerUserId, userId, role, List.of());
        return userId;
    }

    private String token(UUID userId) {
        return "Bearer " + tokenIssuer.issueAccessToken(userId, UUID.randomUUID(), List.of("USER")).token();
    }

    private UUID newTargetUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);
        return userId;
    }

    @Test
    void getCompany_anonymous401_nonMember404_everyDefaultProfile200() throws Exception {
        mockMvc.perform(get("/companies/{id}", companyId)).andExpect(status().isUnauthorized());

        UUID outsider = newTargetUser();
        mockMvc.perform(get("/companies/{id}", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(outsider)))
                .andExpect(status().isNotFound());

        // COMPANY_VIEW is in every default profile + implicit for OWNER
        for (UUID member : List.of(ownerUserId, pmUserId, supervisorUserId, accountantUserId, viewerUserId)) {
            mockMvc.perform(get("/companies/{id}", companyId)
                            .header(HttpHeaders.AUTHORIZATION, token(member)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void getCompany_revokedPermission403_reGrantMakesIt200_withoutTokenRefresh() throws Exception {
        // Revoke VIEWER's COMPANY_VIEW — the SAME token immediately stops working
        jdbcTemplate.update("DELETE FROM company_role_permissions WHERE company_id = ? AND role = 'VIEWER' AND permission = 'COMPANY_VIEW'",
                companyId);
        mockMvc.perform(get("/companies/{id}", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(viewerUserId)))
                .andExpect(status().isForbidden());

        jdbcTemplate.update("INSERT INTO company_role_permissions (company_id, role, permission) VALUES (?, ?, ?)",
                companyId, "VIEWER", "COMPANY_VIEW");
        mockMvc.perform(get("/companies/{id}", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(viewerUserId)))
                .andExpect(status().isOk());
    }

    @Test
    void patchCompany_permissionNotRoleDecides() throws Exception {
        String body = "{\"name\": \"Acme Renamed\", \"gstNumber\": null, \"statementEmail\": null, \"businessTimezone\": null}";

        // OWNER implicit COMPANY_UPDATE
        mockMvc.perform(patch("/companies/{id}", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        // PROCUREMENT_MANAGER lacks COMPANY_UPDATE
        mockMvc.perform(patch("/companies/{id}", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(pmUserId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossCompany_memberOfOtherCompanyOnly_gets404() throws Exception {
        UUID otherCompanyId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO companies (id, name) VALUES (?, 'Other')", otherCompanyId);
        UUID outsider = newTargetUser();
        jdbcTemplate.update("INSERT INTO company_members (id, company_id, user_id, role) VALUES (?, ?, ?, 'OWNER')",
                UUID.randomUUID(), otherCompanyId, outsider);

        mockMvc.perform(get("/companies/{id}", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(outsider)))
                .andExpect(status().isNotFound());
    }

    @Test
    void members_administration_requiresPermission_ownerImplicit() throws Exception {
        UUID target = newTargetUser();
        String body = "{\"memberUserId\": \"" + target + "\", \"role\": \"VIEWER\", \"siteIds\": []}";

        mockMvc.perform(post("/companies/{id}/members", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        // SITE_SUPERVISOR: no MEMBER_MANAGE
        mockMvc.perform(post("/companies/{id}/members", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(supervisorUserId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void sites_viewAllowedByDefault_manageRequiresPermission() throws Exception {
        mockMvc.perform(get("/companies/{id}/sites", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(supervisorUserId)))
                .andExpect(status().isOk());

        String body = "{\"name\": \"Warehouse\", \"addressId\": null, \"active\": null}";
        mockMvc.perform(post("/companies/{id}/sites", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(accountantUserId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/companies/{id}/sites", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void applicationAdminWithoutMembership_gets404_notCompanyPowers() throws Exception {
        // Application ADMIN is a separate authority domain: no membership, no access
        String adminToken = "Bearer " + tokenIssuer
                .issueAccessToken(newTargetUser(), UUID.randomUUID(), List.of("ADMIN")).token();
        mockMvc.perform(get("/companies/{id}", companyId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void staleRoleInToken_authorizationUsesDatabase() throws Exception {
        // Token claims VIEWER (mismatched on purpose): membership in DB is what counts.
        // The DB says OWNER, so the operation succeeds — proving the claim is not the
        // authorization source (and symmetric case in CompanyPermissionMatrixIT).
        String mismatchedToken = "Bearer " + tokenIssuer.issueAccessToken(ownerUserId, UUID.randomUUID(),
                List.of("USER"),
                List.of(new B2bMembership(companyId, CompanyRole.VIEWER, List.of()))).token();

        String body = "{\"name\": \"Claim Mismatch\", \"gstNumber\": null, \"statementEmail\": null, \"businessTimezone\": null}";
        mockMvc.perform(patch("/companies/{id}", companyId)
                        .header(HttpHeaders.AUTHORIZATION, mismatchedToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }
}
