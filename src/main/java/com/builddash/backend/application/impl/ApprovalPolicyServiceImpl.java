package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.ApprovalPolicyService;
import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.ApprovalPolicy;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.port.ApprovalPolicyRepository;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.infra.config.ApprovalProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApprovalPolicyServiceImpl implements ApprovalPolicyService {

    private final B2bAuthorizer b2bAuthorizer;
    private final ApprovalPolicyRepository policyRepository;
    private final CompanyMemberRepository memberRepository;
    private final ApprovalProperties approvalProperties;

    @Override
    @Transactional(readOnly = true)
    public ApprovalPolicy get(UUID userId, UUID companyId) {
        b2bAuthorizer.authorize(userId, companyId, CompanyPermission.COMPANY_VIEW, null, false);
        return policyRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new NotFoundException("APPROVAL_POLICY_NOT_FOUND",
                        "No approval policy configured for company " + companyId));
    }

    @Override
    @Transactional
    public ApprovalPolicy put(UUID userId, UUID companyId, Command command) {
        // Critical authz takes the COMPANY row first (global lock order). OWNER-only by
        // structural role check — no POLICY_MANAGE permission exists and none may be
        // granted: a non-OWNER holding every other permission is still rejected here.
        b2bAuthorizer.authorize(userId, companyId, CompanyPermission.COMPANY_UPDATE, null, true);
        CompanyMember member = memberRepository.findByCompanyIdAndUserId(companyId, userId)
                .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND",
                        "Company not found: " + companyId));
        if (member.role() != CompanyRole.OWNER) {
            throw new ForbiddenException("OWNER_ONLY",
                    "Only the company OWNER can manage the approval policy");
        }

        int escalationHours = command.escalationHours() != null
                ? command.escalationHours()
                : approvalProperties.getStageHours();
        ApprovalPolicy updated = policyRepository.findByCompanyId(companyId)
                .map(existing -> existing.replaced(command.amountThreshold(), command.categoryIds(),
                        command.siteIds(), command.roleStages(), escalationHours, Instant.now()))
                .orElseGet(() -> new ApprovalPolicy(UUID.randomUUID(), companyId,
                        command.amountThreshold(), command.categoryIds(), command.siteIds(),
                        command.roleStages(), escalationHours, 1, null, null));
        return policyRepository.save(updated);
    }
}
