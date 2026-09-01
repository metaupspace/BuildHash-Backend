package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.model.ApprovalPolicy;
import com.builddash.backend.domain.port.ApprovalPolicyRepository;
import com.builddash.backend.infra.persistence.entity.ApprovalPolicyEntity;
import com.builddash.backend.infra.persistence.repository.ApprovalPolicyJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class ApprovalPolicyRepositoryAdapter implements ApprovalPolicyRepository {

    private final ApprovalPolicyJpaRepository jpaRepository;

    @Override
    public Optional<ApprovalPolicy> findByCompanyId(UUID companyId) {
        return jpaRepository.findByCompanyId(companyId).map(this::toDomain);
    }

    @Override
    public ApprovalPolicy save(ApprovalPolicy policy) {
        ApprovalPolicyEntity entity = jpaRepository.findById(policy.id())
                .orElseGet(() -> {
                    ApprovalPolicyEntity e = new ApprovalPolicyEntity();
                    e.setId(policy.id());
                    return e;
                });
        entity.setCompanyId(policy.companyId());
        entity.setAmountThreshold(policy.amountThreshold());
        entity.setCategoryIds(policy.categoryIds().isEmpty() ? null : policy.categoryIds());
        entity.setSiteIds(policy.siteIds().isEmpty() ? null : policy.siteIds());
        entity.setRoleStages(policy.roleStages().stream().map(Enum::name).toList());
        entity.setEscalationHours(policy.escalationHours());
        entity.setVersion(policy.version());
        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    private ApprovalPolicy toDomain(ApprovalPolicyEntity e) {
        List<CompanyRole> stages = e.getRoleStages() == null ? List.of()
                : e.getRoleStages().stream().map(CompanyRole::valueOf).toList();
        return new ApprovalPolicy(e.getId(), e.getCompanyId(), e.getAmountThreshold(),
                e.getCategoryIds() == null ? List.of() : List.copyOf(e.getCategoryIds()),
                e.getSiteIds() == null ? List.of() : List.copyOf(e.getSiteIds()),
                stages, e.getEscalationHours(), e.getVersion(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
