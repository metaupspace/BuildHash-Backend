package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.CompanyStatus;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.Company;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.domain.port.CompanyRepository;
import com.builddash.backend.domain.port.CompanyRolePermissionRepository;
import com.builddash.backend.domain.port.CompanySiteAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class B2bAuthorizerImpl implements B2bAuthorizer {

    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final CompanyRolePermissionRepository companyRolePermissionRepository;
    private final CompanySiteAssignmentRepository companySiteAssignmentRepository;

    @Override
    @Transactional
    public void authorize(UUID userId, UUID companyId, CompanyPermission requiredPermission,
                          UUID resourceSiteId, boolean critical) {
        // Single serialization point for critical mutations: every admin mutation
        // (membership, permissions, and this) takes the company row first, in the
        // same order, so concurrent authorization-relevant changes cannot interleave.
        if (critical) {
            Company company = companyRepository.findByIdForUpdate(companyId);
            // H0.4: suspension is enforced at this single choke point — every B2B
            // mutation routes through here, so a suspended company is fully inert.
            if (company.status() != CompanyStatus.ACTIVE) {
                throw new ForbiddenException("COMPANY_SUSPENDED",
                        "Company is suspended: " + companyId);
            }
        }

        CompanyMember member = companyMemberRepository
                .findByCompanyIdAndUserId(companyId, userId)
                .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND",
                        "Company not found: " + companyId));

        if (member.role() != CompanyRole.OWNER) {
            Set<CompanyPermission> granted =
                    companyRolePermissionRepository.findPermissions(companyId, member.role());
            if (!granted.contains(requiredPermission)) {
                throw new ForbiddenException("FORBIDDEN",
                        "Permission " + requiredPermission.name() + " is required for this operation");
            }
        }
        // OWNER: implicit ALL permissions — nothing stored, nothing to check.

        if (resourceSiteId != null) {
            List<UUID> assignedSites = companySiteAssignmentRepository
                    .findSiteIdsByMemberId(member.id());
            boolean coversSite = assignedSites.isEmpty() || assignedSites.contains(resourceSiteId);
            if (!coversSite) {
                if (critical) {
                    throw new ForbiddenException("SITE_OUT_OF_SCOPE",
                            "Member is not assigned to the requested site");
                }
                // Reads hide existence like every other non-member resource
                throw new NotFoundException("COMPANY_SITE_NOT_FOUND",
                        "Company site not found: " + resourceSiteId);
            }
        }
    }
}
