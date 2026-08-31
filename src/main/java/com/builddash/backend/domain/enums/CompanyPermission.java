package com.builddash.backend.domain.enums;

/**
 * The complete B2B permission vocabulary — exactly 22 constants, one per capability
 * the Phase 9 endpoints actually need. No permission exists without a guarding
 * endpoint/workflow; adding one is a schema event (V26 CHECK) plus this enum.
 *
 * OWNER holds ALL permissions implicitly. ROLE_PERMISSION_MANAGE is the escalation
 * firewall: only OWNER ever has it (implicitly), and the permission-management
 * service rejects granting it to any non-OWNER role.
 */
public enum CompanyPermission {
    // company + administration
    COMPANY_VIEW,
    COMPANY_UPDATE,
    MEMBER_VIEW,
    MEMBER_MANAGE,
    ROLE_PERMISSION_MANAGE,
    SITE_VIEW,
    SITE_MANAGE,

    // RFQ (9-B)
    RFQ_VIEW,
    RFQ_CREATE,
    RFQ_CANCEL,
    RFQ_CONVERT,
    QUOTE_VIEW,

    // purchase orders (9-C)
    PO_VIEW,
    PO_UPLOAD,
    PO_CONVERT,

    // orders (9-D gate)
    ORDER_VIEW,
    ORDER_CREATE,

    // approvals (9-D)
    APPROVAL_VIEW,
    APPROVAL_ACT,
    APPROVAL_DELEGATE,

    // financial records (9-E + invoice visibility)
    INVOICE_VIEW,
    STATEMENT_VIEW
}
