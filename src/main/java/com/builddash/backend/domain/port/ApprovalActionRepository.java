package com.builddash.backend.domain.port;

import com.builddash.backend.domain.enums.ApprovalActionType;
import com.builddash.backend.domain.model.ApprovalAction;

import java.util.List;
import java.util.UUID;

public interface ApprovalActionRepository {

    ApprovalAction save(ApprovalAction action);

    List<ApprovalAction> findByRequestId(UUID requestId);

    boolean existsByRequestIdAndTypeAndStageIndex(UUID requestId, ApprovalActionType type, int stageIndex);
}
