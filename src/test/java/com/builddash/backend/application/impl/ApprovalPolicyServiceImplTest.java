package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.ApprovalPolicyService;
import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.exception.ApprovalPolicyValidationException;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.ApprovalPolicy;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.port.ApprovalPolicyRepository;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.infra.config.ApprovalProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalPolicyServiceImplTest {

    @Mock
    private B2bAuthorizer b2bAuthorizer;
    @Mock
    private ApprovalPolicyRepository policyRepository;
    @Mock
    private CompanyMemberRepository memberRepository;

    @Spy
    private ApprovalProperties approvalProperties = new ApprovalProperties();

    @InjectMocks
    private ApprovalPolicyServiceImpl policyService;

    private final UUID userId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    private CompanyMember memberWithRole(CompanyRole role) {
        return new CompanyMember(UUID.randomUUID(), companyId, userId, role, null, null);
    }

    @Test
    void get_whenNoPolicy_throwsNotFound() {
        when(policyRepository.findByCompanyId(companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policyService.get(userId, companyId))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "APPROVAL_POLICY_NOT_FOUND");
    }

    @Test
    void get_authorizesCompanyViewNonCritical() {
        ApprovalPolicy policy = new ApprovalPolicy(UUID.randomUUID(), companyId, null, List.of(), List.of(),
                List.of(CompanyRole.OWNER), 24, 1, null, null);
        when(policyRepository.findByCompanyId(companyId)).thenReturn(Optional.of(policy));

        ApprovalPolicy result = policyService.get(userId, companyId);

        assertThat(result.companyId()).isEqualTo(companyId);
        verify(b2bAuthorizer).authorize(userId, companyId, CompanyPermission.COMPANY_VIEW, null, false);
    }

    @Test
    void put_owner_createsPolicyWithDefaults() {
        when(memberRepository.findByCompanyIdAndUserId(companyId, userId))
                .thenReturn(Optional.of(memberWithRole(CompanyRole.OWNER)));
        when(policyRepository.findByCompanyId(companyId)).thenReturn(Optional.empty());
        when(policyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalPolicy saved = policyService.put(userId, companyId, new ApprovalPolicyService.Command(
                new BigDecimal("5000.00"), null, null, List.of(CompanyRole.PROCUREMENT_MANAGER, CompanyRole.OWNER), null));

        verify(b2bAuthorizer).authorize(userId, companyId, CompanyPermission.COMPANY_UPDATE, null, true);
        assertThat(saved.version()).isEqualTo(1);
        assertThat(saved.escalationHours()).isEqualTo(24); // config default
        assertThat(saved.amountThreshold()).isEqualByComparingTo("5000.00");
    }

    @Test
    void put_owner_replacesAndIncrementsVersion() {
        when(memberRepository.findByCompanyIdAndUserId(companyId, userId))
                .thenReturn(Optional.of(memberWithRole(CompanyRole.OWNER)));
        ApprovalPolicy existing = new ApprovalPolicy(UUID.randomUUID(), companyId,
                new BigDecimal("5000.00"), List.of(), List.of(),
                List.of(CompanyRole.OWNER), 24, 4, null, null);
        when(policyRepository.findByCompanyId(companyId)).thenReturn(Optional.of(existing));
        when(policyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApprovalPolicy saved = policyService.put(userId, companyId, new ApprovalPolicyService.Command(
                null, null, null, List.of(CompanyRole.OWNER), 12));

        assertThat(saved.version()).isEqualTo(5);
        assertThat(saved.escalationHours()).isEqualTo(12);
        assertThat(saved.amountThreshold()).isNull();
    }

    @Test
    void put_nonOwner_rejectedEvenWithUnrelatedPermissions() {
        when(memberRepository.findByCompanyIdAndUserId(companyId, userId))
                .thenReturn(Optional.of(memberWithRole(CompanyRole.PROCUREMENT_MANAGER)));
        // Critical authz PASSED (member holds COMPANY_UPDATE, say) — structural check must still reject.

        assertThatThrownBy(() -> policyService.put(userId, companyId, new ApprovalPolicyService.Command(
                null, null, null, List.of(CompanyRole.OWNER), null)))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "OWNER_ONLY");
        verify(policyRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void put_emptyRoleStages_throwsValidation() {
        when(memberRepository.findByCompanyIdAndUserId(companyId, userId))
                .thenReturn(Optional.of(memberWithRole(CompanyRole.OWNER)));

        assertThatThrownBy(() -> policyService.put(userId, companyId, new ApprovalPolicyService.Command(
                null, null, null, List.of(), null)))
                .isInstanceOf(ApprovalPolicyValidationException.class)
                .hasFieldOrPropertyWithValue("code", "POLICY_ROLE_STAGES_REQUIRED");
    }

    @Test
    void put_duplicateRoleStages_throwsValidation() {
        when(memberRepository.findByCompanyIdAndUserId(companyId, userId))
                .thenReturn(Optional.of(memberWithRole(CompanyRole.OWNER)));

        assertThatThrownBy(() -> policyService.put(userId, companyId, new ApprovalPolicyService.Command(
                null, null, null, List.of(CompanyRole.OWNER, CompanyRole.OWNER), null)))
                .isInstanceOf(ApprovalPolicyValidationException.class)
                .hasFieldOrPropertyWithValue("code", "POLICY_ROLE_STAGES_DISTINCT");
    }

    @Test
    void put_zeroEscalationHours_throwsValidation() {
        when(memberRepository.findByCompanyIdAndUserId(companyId, userId))
                .thenReturn(Optional.of(memberWithRole(CompanyRole.OWNER)));

        assertThatThrownBy(() -> policyService.put(userId, companyId, new ApprovalPolicyService.Command(
                null, null, null, List.of(CompanyRole.OWNER), 0)))
                .isInstanceOf(ApprovalPolicyValidationException.class)
                .hasFieldOrPropertyWithValue("code", "POLICY_ESCALATION_HOURS_INVALID");
    }

    @Test
    void put_negativeThreshold_throwsValidation() {
        when(memberRepository.findByCompanyIdAndUserId(companyId, userId))
                .thenReturn(Optional.of(memberWithRole(CompanyRole.OWNER)));

        assertThatThrownBy(() -> policyService.put(userId, companyId, new ApprovalPolicyService.Command(
                new BigDecimal("-1.00"), null, null, List.of(CompanyRole.OWNER), null)))
                .isInstanceOf(ApprovalPolicyValidationException.class)
                .hasFieldOrPropertyWithValue("code", "POLICY_THRESHOLD_INVALID");
    }
}
