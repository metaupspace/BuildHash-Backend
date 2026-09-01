package com.builddash.backend.domain.enums;

/** Approval request lifecycle (9-D). PENDING may persist indefinitely — there is no
 *  automatic approval timeout or cancellation (locked decision 4). */
public enum ApprovalRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
