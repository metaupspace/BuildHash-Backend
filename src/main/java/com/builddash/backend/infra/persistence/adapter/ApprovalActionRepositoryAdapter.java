package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.ApprovalActionType;
import com.builddash.backend.domain.model.ApprovalAction;
import com.builddash.backend.domain.port.ApprovalActionRepository;
import com.builddash.backend.infra.persistence.entity.ApprovalActionEntity;
import com.builddash.backend.infra.persistence.repository.ApprovalActionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class ApprovalActionRepositoryAdapter implements ApprovalActionRepository {

    private final ApprovalActionJpaRepository jpaRepository;

    @Override
    public ApprovalAction save(ApprovalAction action) {
        ApprovalActionEntity entity = new ApprovalActionEntity();
        entity.setId(action.id());
        entity.setRequestId(action.requestId());
        entity.setActionType(action.type().name());
        entity.setActorMemberId(action.actorMemberId());
        entity.setDelegateMemberId(action.delegateMemberId());
        entity.setStageIndex(action.stageIndex());
        entity.setDetail(action.detail());
        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public List<ApprovalAction> findByRequestId(UUID requestId) {
        return jpaRepository.findByRequestIdOrderByCreatedAtAsc(requestId).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public boolean existsByRequestIdAndTypeAndStageIndex(UUID requestId, ApprovalActionType type, int stageIndex) {
        return jpaRepository.existsByRequestIdAndActionTypeAndStageIndex(
                requestId, type.name(), stageIndex);
    }

    private ApprovalAction toDomain(ApprovalActionEntity e) {
        return new ApprovalAction(e.getId(), e.getRequestId(), ApprovalActionType.valueOf(e.getActionType()),
                e.getActorMemberId(), e.getDelegateMemberId(), e.getStageIndex(), e.getDetail(),
                e.getCreatedAt());
    }
}
