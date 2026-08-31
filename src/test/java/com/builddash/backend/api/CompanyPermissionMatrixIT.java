package com.builddash.backend.api;

import com.builddash.backend.application.service.CompanyService;
import com.builddash.backend.domain.enums.CompanyRole;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The OWNER-only permission-management surface end to end: defaults seeded at company
 * creation, GET/PUT behavior, OWNER immutability, escalation firewall, self-grant
 * rejection for every non-OWNER role, per-company isolation (same role, different
 * permissions), and multi-company memberships with different roles per company.
 */
class CompanyPermissionMatrixIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private com.builddash.backend.application.service.CompanyMembershipService membershipService;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID companyId;
    private UUID otherCompanyId;
    private UUID ownerUserId;

    @BeforeEach
    void setUp() {
        ownerUserId = newUser();
        companyId = companyService.create(ownerUserId, "Acme", null, null, null).id();
        otherCompanyId = companyService.create(newUser(), "Beta", null, null, null).id();
        // Beta's creator is its OWNER — fetch that user id for member administration
        otherOwnerUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM company_members WHERE company_id = ? AND role = 'OWNER'",
                UUID.class, otherCompanyId);
    }

    private UUID otherOwnerUserId;

    private UUID newUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);
        return userId;
    }

    private UUID member(UUID company, UUID actorOwner, CompanyRole role) {
        UUID userId = newUser();
        membershipService.addMember(company, actorOwner, userId, role, List.of());
        return userId;
    }

    private String token(UUID userId, List<String> roles) {
        return "Bearer " + tokenIssuer.issueAccessToken(userId, UUID.randomUUID(), roles).token();
    }

    @Test
    void creationSeedsDefaultProfiles_ownerImplicitAll() throws Exception {
        mockMvc.perform(get("/companies/{id}/role-permissions", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(ownerUserId, List.of("USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.OWNER.immutable").value(true))
                .andExpect(jsonPath("$.roles.OWNER.permissions.length()").value(22))
                .andExpect(jsonPath("$.roles.PROCUREMENT_MANAGER.immutable").value(false))
                .andExpect(jsonPath("$.roles.PROCUREMENT_MANAGER.permissions.length()").value(12))
                .andExpect(jsonPath("$.roles.SITE_SUPERVISOR.permissions.length()").value(6))
                .andExpect(jsonPath("$.roles.ACCOUNTANT.permissions.length()").value(4))
                .andExpect(jsonPath("$.roles.VIEWER.permissions.length()").value(5));
    }

    @Test
    void put_ownerImmutable_422() throws Exception {
        mockMvc.perform(put("/companies/{id}/role-permissions/OWNER", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(ownerUserId, List.of("USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\": [\"COMPANY_VIEW\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OWNER_PERMISSIONS_IMMUTABLE"));
    }

    @Test
    void put_grantingRolePermissionManage_422_firewall() throws Exception {
        mockMvc.perform(put("/companies/{id}/role-permissions/VIEWER", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(ownerUserId, List.of("USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\": [\"COMPANY_VIEW\", \"ROLE_PERMISSION_MANAGE\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PERMISSION_ESCALATION_GUARDED"));
    }

    @Test
    void put_unknownPermission_400() throws Exception {
        mockMvc.perform(put("/companies/{id}/role-permissions/VIEWER", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(ownerUserId, List.of("USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\": [\"NOT_A_PERMISSION\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noRoleCanSelfGrant_onlyOwnerManagesPermissions() throws Exception {
        String body = "{\"permissions\": [\"COMPANY_VIEW\"]}";
        for (CompanyRole role : List.of(CompanyRole.PROCUREMENT_MANAGER, CompanyRole.SITE_SUPERVISOR,
                CompanyRole.ACCOUNTANT, CompanyRole.VIEWER)) {
            UUID userId = member(companyId, ownerUserId, role);
            mockMvc.perform(put("/companies/{id}/role-permissions/VIEWER", companyId)
                            .header(HttpHeaders.AUTHORIZATION, token(userId, List.of("USER")))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void put_effectiveImmediately_withoutTokenRefresh() throws Exception {
        UUID viewerUserId = member(companyId, ownerUserId, CompanyRole.VIEWER);

        // SITE_VIEW is in VIEWER's default set: visible via company sites
        mockMvc.perform(get("/companies/{id}/sites", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(viewerUserId, List.of("USER"))))
                .andExpect(status().isOk());

        // OWNER replaces VIEWER's set to a bare COMPANY_VIEW
        mockMvc.perform(put("/companies/{id}/role-permissions/VIEWER", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(ownerUserId, List.of("USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\": [\"COMPANY_VIEW\"]}"))
                .andExpect(status().isOk());

        // Same viewer token: sites now 403 (SITE_VIEW revoked, no re-login)
        mockMvc.perform(get("/companies/{id}/sites", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(viewerUserId, List.of("USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void perCompanyIsolation_sameRoleDifferentPermissions() throws Exception {
        // Beta's VIEWER keeps full default set; Acme's VIEWER is stripped
        mockMvc.perform(put("/companies/{id}/role-permissions/VIEWER", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(ownerUserId, List.of("USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\": []}"))
                .andExpect(status().isOk());

        UUID acmeViewer = member(companyId, ownerUserId, CompanyRole.VIEWER);
        UUID betaViewer = member(otherCompanyId, otherOwnerUserId, CompanyRole.VIEWER);

        mockMvc.perform(get("/companies/{id}/sites", companyId)
                        .header(HttpHeaders.AUTHORIZATION, token(acmeViewer, List.of("USER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/companies/{id}/sites", otherCompanyId)
                        .header(HttpHeaders.AUTHORIZATION, token(betaViewer, List.of("USER"))))
                .andExpect(status().isOk());
    }

    @Test
    void multiCompanyMembership_differentRolesPerCompany() throws Exception {
        UUID dualUserId = newUser();
        // OWNER in Acme (implicit all), VIEWER in Beta
        membershipService.addMember(companyId, ownerUserId, dualUserId, CompanyRole.OWNER, List.of());
        membershipService.addMember(otherCompanyId, otherOwnerUserId, dualUserId, CompanyRole.VIEWER, List.of());

        String dualToken = token(dualUserId, List.of("USER"));
        mockMvc.perform(get("/companies/{id}/role-permissions", companyId)
                        .header(HttpHeaders.AUTHORIZATION, dualToken))
                .andExpect(status().isOk()); // OWNER in Acme
        mockMvc.perform(get("/companies/{id}/role-permissions", otherCompanyId)
                        .header(HttpHeaders.AUTHORIZATION, dualToken))
                .andExpect(status().isForbidden()); // VIEWER in Beta
    }
}
