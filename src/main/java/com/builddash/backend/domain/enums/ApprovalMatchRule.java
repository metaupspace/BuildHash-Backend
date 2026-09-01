package com.builddash.backend.domain.enums;

/** Which policy conditions matched at gate time. OR semantics — several can match. */
public enum ApprovalMatchRule {
    AMOUNT,
    CATEGORY,
    SITE
}
