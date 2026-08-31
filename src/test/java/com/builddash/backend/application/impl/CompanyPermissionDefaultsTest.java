package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.service.CompanyPermissionDefaults;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Permission-model shape contracts: exactly five roles with NO hierarchy methods,
 * exactly 22 permissions, approved default profiles, and OWNER/profile hygiene
 * (no stored OWNER set, ROLE_PERMISSION_MANAGE in no profile).
 */
class CompanyPermissionDefaultsTest {

    @Test
    void companyRole_exactlyFiveValues_noHierarchyMethods() {
        assertThat(CompanyRole.values()).containsExactlyInAnyOrder(
                CompanyRole.OWNER, CompanyRole.PROCUREMENT_MANAGER, CompanyRole.SITE_SUPERVISOR,
                CompanyRole.ACCOUNTANT, CompanyRole.VIEWER);
        for (java.lang.reflect.Method method : CompanyRole.class.getDeclaredMethods()) {
            assertThat(method.getName()).as("no hierarchy machinery on CompanyRole: %s", method)
                    .doesNotContain("rank", "atLeast");
        }
    }

    @Test
    void companyPermission_exactlyTheApproved22() {
        assertThat(CompanyPermission.values()).hasSize(22);
    }

    @Test
    void defaultProfiles_matchApprovedSets() {
        assertThat(CompanyPermissionDefaults.forRole(CompanyRole.PROCUREMENT_MANAGER)).containsExactlyInAnyOrder(
                CompanyPermission.COMPANY_VIEW, CompanyPermission.RFQ_VIEW, CompanyPermission.RFQ_CREATE,
                CompanyPermission.RFQ_CANCEL, CompanyPermission.RFQ_CONVERT, CompanyPermission.QUOTE_VIEW,
                CompanyPermission.PO_VIEW, CompanyPermission.PO_UPLOAD, CompanyPermission.PO_CONVERT,
                CompanyPermission.ORDER_VIEW, CompanyPermission.ORDER_CREATE, CompanyPermission.APPROVAL_VIEW);

        assertThat(CompanyPermissionDefaults.forRole(CompanyRole.SITE_SUPERVISOR)).containsExactlyInAnyOrder(
                CompanyPermission.COMPANY_VIEW, CompanyPermission.SITE_VIEW, CompanyPermission.ORDER_VIEW,
                CompanyPermission.PO_VIEW, CompanyPermission.RFQ_VIEW, CompanyPermission.APPROVAL_VIEW);

        assertThat(CompanyPermissionDefaults.forRole(CompanyRole.ACCOUNTANT)).containsExactlyInAnyOrder(
                CompanyPermission.COMPANY_VIEW, CompanyPermission.ORDER_VIEW,
                CompanyPermission.INVOICE_VIEW, CompanyPermission.STATEMENT_VIEW);

        assertThat(CompanyPermissionDefaults.forRole(CompanyRole.VIEWER)).containsExactlyInAnyOrder(
                CompanyPermission.COMPANY_VIEW, CompanyPermission.SITE_VIEW, CompanyPermission.ORDER_VIEW,
                CompanyPermission.RFQ_VIEW, CompanyPermission.PO_VIEW);
    }

    @Test
    void ownerHasNoStoredProfile_escalationPermissionInNoProfile() {
        assertThat(CompanyPermissionDefaults.forRole(CompanyRole.OWNER)).isEmpty();
        assertThat(CompanyPermissionDefaults.customizableRoles()).doesNotContain(CompanyRole.OWNER);

        for (CompanyRole role : CompanyPermissionDefaults.customizableRoles()) {
            assertThat(CompanyPermissionDefaults.forRole(role))
                    .as("ROLE_PERMISSION_MANAGE must not appear in any default profile: %s", role)
                    .doesNotContain(CompanyPermission.ROLE_PERMISSION_MANAGE);
        }
    }

    @Test
    void everyPermissionIsValidEnumValue_roundTrip() {
        for (CompanyPermission permission : CompanyPermission.values()) {
            assertThat(CompanyPermission.valueOf(permission.name())).isEqualTo(permission);
        }
        // The exact vocabulary strings the V26 CHECK constraint accepts
        Set<String> names = java.util.Arrays.stream(CompanyPermission.values())
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(names).contains("COMPANY_VIEW", "STATEMENT_VIEW", "APPROVAL_DELEGATE", "PO_UPLOAD");
    }
}
