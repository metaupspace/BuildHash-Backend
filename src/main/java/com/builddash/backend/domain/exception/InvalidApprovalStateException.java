package com.builddash.backend.domain.exception;

import com.builddash.backend.domain.enums.ApprovalRequestStatus;

public class InvalidApprovalStateException extends DomainException {

    public static InvalidApprovalStateException notPending(ApprovalRequestStatus current) {
        return new InvalidApprovalStateException("APPROVAL_NOT_PENDING",
                "Approval request is " + current.name() + ", expected PENDING");
    }

    public static InvalidApprovalStateException orderNotPendingApproval(String currentStatus) {
        return new InvalidApprovalStateException("ORDER_NOT_PENDING_APPROVAL",
                "Order is " + currentStatus + ", expected PENDING_APPROVAL");
    }

    public static InvalidApprovalStateException alreadyDelegated() {
        return new InvalidApprovalStateException("APPROVAL_ALREADY_DELEGATED",
                "Approval request already has an assigned delegate; re-delegation is not allowed");
    }

    public InvalidApprovalStateException(String code, String message) {
        super(code, message);
    }
}
