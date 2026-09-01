package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.ApprovalAction;
import com.builddash.backend.domain.model.ApprovalRequest;
import com.builddash.backend.domain.model.Order;

import java.util.List;
import java.util.UUID;

/** Approval decision surface (9-D): list, detail, approve, reject, delegate. */
public interface ApprovalService {

    /** APPROVAL_VIEW + company + site scope. Null-site requests: all-site members only. */
    List<ApprovalRequest> list(UUID userId, UUID companyId);

    /** Same visibility rules as list, per resource. */
    ApprovalDetail get(UUID userId, UUID approvalId);

    /**
     * APPROVAL_ACT decision. On success: order resumes PAYMENT_PENDING, request
     * APPROVED, payment initiated outside the transaction. On slot reacquisition
     * failure the cancellation COMMITS (order/request CANCELLED, origin
     * APPROVAL_SLOT_UNAVAILABLE) and SlotUnavailableException surfaces post-commit.
     */
    ApprovalDetail approve(UUID actorUserId, UUID approvalId);

    /** APPROVAL_ACT decision. Order CANCELLED, request REJECTED, no slot/payment. */
    ApprovalDetail reject(UUID actorUserId, UUID approvalId);

    /** APPROVAL_DELEGATE, single-hop, request-scoped. */
    ApprovalDetail delegate(UUID actorUserId, UUID approvalId, UUID delegateMemberId);

    record ApprovalDetail(
            ApprovalRequest request,
            Order order,
            List<ApprovalAction> actions
    ) {
    }
}
