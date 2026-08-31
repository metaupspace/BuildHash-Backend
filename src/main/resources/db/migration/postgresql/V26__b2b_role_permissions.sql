-- Checkpoint 9-A.1: B2B authorization model correction.
--
-- 1. Replace the obsolete role vocabulary (OWNER/ADMIN/APPROVER/BUYER from 9-A) with
--    the five responsibility profiles. Roles carry NO hierarchy — capabilities come
--    from company-customizable permission rows below.
-- 2. company_role_permissions holds the effective, per-company permission set for the
--    four non-OWNER roles. OWNER permissions are implicit ALL and immutable — the
--    CHECK constraint makes OWNER rows unrepresentable, so a lockout state cannot
--    exist in the database.
--
-- Data mapping for pre-existing dev/test rows (no production data exists):
--   ADMIN -> OWNER, APPROVER -> PROCUREMENT_MANAGER, BUYER -> PROCUREMENT_MANAGER.

ALTER TABLE company_members DROP CONSTRAINT chk_company_members_role;

UPDATE company_members SET role = CASE
    WHEN role = 'ADMIN'    THEN 'OWNER'
    WHEN role = 'APPROVER' THEN 'PROCUREMENT_MANAGER'
    WHEN role = 'BUYER'    THEN 'PROCUREMENT_MANAGER'
    ELSE role
END;

ALTER TABLE company_members ADD CONSTRAINT chk_company_members_role
    CHECK (role IN ('OWNER', 'PROCUREMENT_MANAGER', 'SITE_SUPERVISOR', 'ACCOUNTANT', 'VIEWER'));

-- Effective per-company permissions. PK gives set semantics + dedup; the role CHECK
-- excludes OWNER (implicit ALL, never stored); the permission CHECK makes typos and
-- future-drift unrepresentable at the database level.
CREATE TABLE company_role_permissions (
    company_id  UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    role        VARCHAR(26) NOT NULL,
    permission  VARCHAR(40) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (company_id, role, permission),
    CONSTRAINT chk_crp_role CHECK (role IN ('PROCUREMENT_MANAGER', 'SITE_SUPERVISOR', 'ACCOUNTANT', 'VIEWER')),
    CONSTRAINT chk_crp_permission CHECK (permission IN (
        'COMPANY_VIEW', 'COMPANY_UPDATE',
        'MEMBER_VIEW', 'MEMBER_MANAGE',
        'ROLE_PERMISSION_MANAGE',
        'SITE_VIEW', 'SITE_MANAGE',
        'RFQ_VIEW', 'RFQ_CREATE', 'RFQ_CANCEL', 'RFQ_CONVERT', 'QUOTE_VIEW',
        'PO_VIEW', 'PO_UPLOAD', 'PO_CONVERT',
        'ORDER_VIEW', 'ORDER_CREATE',
        'APPROVAL_VIEW', 'APPROVAL_ACT', 'APPROVAL_DELEGATE',
        'INVOICE_VIEW', 'STATEMENT_VIEW'))
);

-- findPermissions(companyId, role) is covered by the PK's leading columns;
-- findRolesWithPermission(companyId, permission) needs this direction.
CREATE INDEX idx_crp_company_permission ON company_role_permissions (company_id, permission);

-- Backfill: every existing company receives the approved default profiles.
-- ROLE_PERMISSION_MANAGE is deliberately absent — it is OWNER-only and implicit.
INSERT INTO company_role_permissions (company_id, role, permission)
SELECT c.id, 'PROCUREMENT_MANAGER', p.permission FROM companies c
CROSS JOIN (VALUES
    ('COMPANY_VIEW'), ('RFQ_VIEW'), ('RFQ_CREATE'), ('RFQ_CANCEL'), ('RFQ_CONVERT'),
    ('QUOTE_VIEW'), ('PO_VIEW'), ('PO_UPLOAD'), ('PO_CONVERT'), ('ORDER_VIEW'),
    ('ORDER_CREATE'), ('APPROVAL_VIEW')
) AS p(permission)
ON CONFLICT DO NOTHING;

INSERT INTO company_role_permissions (company_id, role, permission)
SELECT c.id, 'SITE_SUPERVISOR', p.permission FROM companies c
CROSS JOIN (VALUES
    ('COMPANY_VIEW'), ('SITE_VIEW'), ('ORDER_VIEW'), ('PO_VIEW'), ('RFQ_VIEW'), ('APPROVAL_VIEW')
) AS p(permission)
ON CONFLICT DO NOTHING;

INSERT INTO company_role_permissions (company_id, role, permission)
SELECT c.id, 'ACCOUNTANT', p.permission FROM companies c
CROSS JOIN (VALUES
    ('COMPANY_VIEW'), ('ORDER_VIEW'), ('INVOICE_VIEW'), ('STATEMENT_VIEW')
) AS p(permission)
ON CONFLICT DO NOTHING;

INSERT INTO company_role_permissions (company_id, role, permission)
SELECT c.id, 'VIEWER', p.permission FROM companies c
CROSS JOIN (VALUES
    ('COMPANY_VIEW'), ('SITE_VIEW'), ('ORDER_VIEW'), ('RFQ_VIEW'), ('PO_VIEW')
) AS p(permission)
ON CONFLICT DO NOTHING;
