package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.CompanyStatus;
import com.builddash.backend.domain.model.B2bMembership;
import com.builddash.backend.domain.port.CompanyMembershipResolver;
import com.builddash.backend.infra.persistence.entity.CompanyMemberEntity;
import com.builddash.backend.infra.persistence.entity.CompanySiteAssignmentEntity;
import com.builddash.backend.infra.persistence.repository.CompanyJpaRepository;
import com.builddash.backend.infra.persistence.repository.CompanyMemberJpaRepository;
import com.builddash.backend.infra.persistence.repository.CompanySiteAssignmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Token-issuance read side: a user's memberships (one B2bMembership per company) with
 * site scope. Only ACTIVE companies contribute context — a suspended company's role
 must not ride in fresh tokens. Called at login/refresh only (decision 4), so the
 per-request path stays free of membership reads.
 */
@Component
@RequiredArgsConstructor
public class CompanyMembershipResolverAdapter implements CompanyMembershipResolver {

    private final CompanyMemberJpaRepository memberJpaRepository;
    private final CompanySiteAssignmentJpaRepository assignmentJpaRepository;
    private final CompanyJpaRepository companyJpaRepository;

    @Override
    public List<B2bMembership> resolveByUserId(UUID userId) {
        List<CompanyMemberEntity> members = memberJpaRepository.findByUserId(userId);
        List<B2bMembership> result = new ArrayList<>();
        for (CompanyMemberEntity member : members) {
            boolean activeCompany = companyJpaRepository.findById(member.getCompanyId())
                    .map(c -> c.getStatus() == CompanyStatus.ACTIVE)
                    .orElse(false);
            if (!activeCompany) {
                continue;
            }
            List<UUID> siteIds = assignmentJpaRepository.findByMemberId(member.getId()).stream()
                    .map(CompanySiteAssignmentEntity::getSiteId)
                    .toList();
            result.add(new B2bMembership(member.getCompanyId(), member.getRole(), siteIds));
        }
        return result;
    }
}
