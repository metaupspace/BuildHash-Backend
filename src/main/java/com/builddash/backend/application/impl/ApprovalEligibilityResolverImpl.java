package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.ApprovalEligibilityResolver;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.domain.port.CompanyRolePermissionRepository;
import com.builddash.backend.domain.port.CompanySiteAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApprovalEligibilityResolverImpl implements ApprovalEligibilityResolver {

    private final CompanyMemberRepository memberRepository;
    private final CompanyRolePermissionRepository rolePermissionRepository;
    private final CompanySiteAssignmentRepository siteAssignmentRepository;

    @Override
    public List<CompanyMember> eligibleApprovers(UUID companyId, CompanyRole stageRole, UUID siteId, UUID placerUserId) {
        // Roles currently holding APPROVAL_ACT — live state, never cached; OWNER passes implicitly.
        Set<CompanyRole> actRoles = rolePermissionRepository.findRolesWithPermission(
                companyId, CompanyPermission.APPROVAL_ACT);

        List<CompanyMember> eligible = new ArrayList<>();
        for (CompanyMember member : memberRepository.findByCompanyId(companyId)) {
            if (member.userId().equals(placerUserId)) {
                continue; // self-approval universally prohibited
            }
            if (member.role() != stageRole) {
                continue; // policy stage role — configuration, never a hierarchy
            }
            if (member.role() != CompanyRole.OWNER && !actRoles.contains(member.role())) {
                continue; // a configured role alone grants nothing without live APPROVAL_ACT
            }
            if (!coversSite(member.id(), siteId)) {
                continue;
            }
            eligible.add(member);
        }
        return eligible;
    }

    @Override
    public boolean hasEligibleApprover(UUID companyId, CompanyRole stageRole, UUID siteId, UUID placerUserId) {
        return !eligibleApprovers(companyId, stageRole, siteId, placerUserId).isEmpty();
    }

    /**
     * ponytail: per-member site-assignment query — bounded by company headcount; a single
     * join query only if a company grows past hundreds of members.
     */
    private boolean coversSite(UUID memberId, UUID siteId) {
        List<UUID> assigned = siteAssignmentRepository.findSiteIdsByMemberId(memberId);
        if (siteId == null) {
            // Null-site requests follow company-wide semantics: all-site members only.
            return assigned.isEmpty();
        }
        return assigned.isEmpty() || assigned.contains(siteId);
    }
}
