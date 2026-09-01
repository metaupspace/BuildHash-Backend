package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.ApprovalActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApprovalActionJpaRepository extends JpaRepository<ApprovalActionEntity, UUID> {

    List<ApprovalActionEntity> findByRequestIdOrderByCreatedAtAsc(UUID requestId);

    boolean existsByRequestIdAndActionTypeAndStageIndex(
            UUID requestId, String actionType, int stageIndex);
}
