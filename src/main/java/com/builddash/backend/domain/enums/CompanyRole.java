package com.builddash.backend.domain.enums;

/**
 * Company-scoped B2B roles. Distinct from the application-level roles carried in the
 * JWT "roles" claim (USER/GUEST/...): B2B roles travel in the separate "b2b" claim and
 * are never converted into Spring authorities (Phase 9 decision 4).
 *
 * Rank gives authorization checks a deterministic ordering (higher rank = more power):
 * escalation chains and "ADMIN+" style checks compare ranks instead of maintaining
 * role lists at every call site.
 */
public enum CompanyRole {

    BUYER(1),
    APPROVER(2),
    ADMIN(3),
    OWNER(4);

    private final int rank;

    CompanyRole(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    /** True when this role's rank is at least the required role's rank ("ADMIN+"). */
    public boolean atLeast(CompanyRole required) {
        return this.rank >= required.rank;
    }
}
