package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.ApprovalMatchRule;
import com.builddash.backend.domain.enums.ApprovalRequestStatus;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.model.ApprovalRequest;
import com.builddash.backend.domain.port.ApprovalRequestRepository;
import com.builddash.backend.infra.persistence.entity.ApprovalRequestEntity;
import com.builddash.backend.infra.persistence.repository.ApprovalRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class ApprovalRequestRepositoryAdapter implements ApprovalRequestRepository {

    private final ApprovalRequestJpaRepository jpaRepository;

    @Override
    public ApprovalRequest save(ApprovalRequest request) {
        ApprovalRequestEntity entity = jpaRepository.findById(request.id())
                .orElseGet(() -> {
                    ApprovalRequestEntity e = new ApprovalRequestEntity();
                    e.setId(request.id());
                    return e;
                });
        entity.setOrderId(request.orderId());
        entity.setCompanyId(request.companyId());
        entity.setStatus(request.status().name());
        entity.setCurrentStageIndex(request.currentStageIndex());
        entity.setCurrentRole(request.currentRole().name());
        entity.setAssignedMemberId(request.assignedMemberId());
        entity.setEscalationDueAt(request.escalationDueAt());
        entity.setOrderTotalAmount(request.orderTotalAmount());
        entity.setMatchedRules(request.matchedRules().isEmpty() ? null
                : request.matchedRules().stream().map(Enum::name).toList());
        entity.setThresholdAmount(request.thresholdAmount());
        entity.setMatchedCategoryIds(request.matchedCategoryIds().isEmpty() ? null
                : request.matchedCategoryIds());
        entity.setSiteId(request.siteId());
        entity.setRoleStages(request.roleStages().stream().map(Enum::name).toList());
        entity.setEscalationHours(request.escalationHours());
        entity.setPolicyVersion(request.policyVersion());
        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<ApprovalRequest> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<ApprovalRequest> findByOrderId(UUID orderId) {
        return jpaRepository.findByOrderId(orderId).map(this::toDomain);
    }

    @Override
    public Optional<ApprovalRequest> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(this::toDomain);
    }

    @Override
    public Optional<ApprovalRequest> findByOrderIdForUpdate(UUID orderId) {
        return jpaRepository.findByOrderIdForUpdate(orderId).map(this::toDomain);
    }

    @Override
    public List<UUID> findDueIds(Instant now) {
        return jpaRepository.findDueIds(now);
    }

    @Override
    public List<ApprovalRequest> findByCompanyVisibleInSites(UUID companyId, Collection<UUID> siteIds) {
        return findByCompanyVisibleInSites(companyId, siteIds, 0, 20);
    }

    @Override
    public List<ApprovalRequest> findByCompanyVisibleInSites(UUID companyId, Collection<UUID> siteIds, int page, int size) {
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), 50);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(boundedPage, boundedSize);
        List<ApprovalRequestEntity> entities = siteIds == null
                ? jpaRepository.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable)
                : jpaRepository.findByCompanyIdAndSiteIdInOrderByCreatedAtDesc(companyId, siteIds, pageable);
        return entities.stream().map(this::toDomain).toList();
    }

    private ApprovalRequest toDomain(ApprovalRequestEntity e) {
        List<CompanyRole> stages = e.getRoleStages() == null ? List.of()
                : e.getRoleStages().stream().map(CompanyRole::valueOf).toList();
        List<ApprovalMatchRule> rules = e.getMatchedRules() == null ? List.of()
                : e.getMatchedRules().stream().map(ApprovalMatchRule::valueOf).toList();
        return new ApprovalRequest(e.getId(), e.getOrderId(), e.getCompanyId(),
                ApprovalRequestStatus.valueOf(e.getStatus()), e.getCurrentStageIndex(),
                CompanyRole.valueOf(e.getCurrentRole()), e.getAssignedMemberId(),
                e.getEscalationDueAt(), e.getOrderTotalAmount(), rules, e.getThresholdAmount(),
                e.getMatchedCategoryIds() == null ? List.of() : List.copyOf(e.getMatchedCategoryIds()),
                e.getSiteId(), stages, e.getEscalationHours(), e.getPolicyVersion(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
