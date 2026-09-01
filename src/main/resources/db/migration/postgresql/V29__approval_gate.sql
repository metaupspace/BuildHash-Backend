-- 9-D: B2B order approval gate.
--
-- company_approval_policies: one row per company (UNIQUE company_id). Absent row =
-- no gate; B2B checkout follows the ordinary payment flow (locked decision 8).
-- Conditions are OR-combined; every condition is independently optional (NULL/empty
-- never matches). role_stages is an ordered configuration list, NOT a hierarchy.
--
-- approval_requests: one per gated order (UNIQUE order_id), born PENDING_APPROVAL.
-- Snapshot columns are immutable after creation — later policy edits or catalog
-- price changes must never mutate an existing request (only current permissions
-- affect approver eligibility, resolved live at action time).
--
-- approval_actions: append-only audit trail. UNIQUE(request_id, action_type,
-- stage_index) is the database backstop for the "exactly one action per type per
-- stage" invariants — duplicate ESCALATED / ESCALATION_BLOCKED / DELEGATED / final
-- decisions are rejected by the constraint even if two instances race past the
-- application re-checks. Ids are caller-assigned (9-C lesson: no @UuidGenerator,
-- merge must not regenerate ids).

CREATE TABLE company_approval_policies (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id       UUID NOT NULL UNIQUE REFERENCES companies (id),
    amount_threshold NUMERIC(12, 2),
    category_ids     UUID[],
    site_ids         UUID[],
    role_stages      VARCHAR(24)[] NOT NULL,
    escalation_hours INT NOT NULL DEFAULT 24,
    version          INT NOT NULL DEFAULT 1,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_cap_role_stages CHECK (array_length(role_stages, 1) >= 1),
    CONSTRAINT chk_cap_escalation_hours CHECK (escalation_hours >= 1),
    CONSTRAINT chk_cap_threshold_positive CHECK (amount_threshold IS NULL OR amount_threshold >= 0)
);

CREATE TABLE approval_requests (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id             UUID NOT NULL UNIQUE REFERENCES orders (id),
    company_id           UUID NOT NULL REFERENCES companies (id),
    status               VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    current_stage_index  INT NOT NULL DEFAULT 0,
    current_stage_role   VARCHAR(24) NOT NULL,
    assigned_member_id   UUID,
    escalation_due_at    TIMESTAMPTZ,
    -- immutable snapshot from here down
    order_total_amount   NUMERIC(12, 2) NOT NULL,
    matched_rules        VARCHAR(12)[],
    threshold_amount     NUMERIC(12, 2),
    matched_category_ids UUID[],
    site_id              UUID,
    role_stages          VARCHAR(24)[] NOT NULL,
    escalation_hours     INT NOT NULL,
    policy_version       INT NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ar_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_ar_stage CHECK (current_stage_index >= 0 AND current_stage_index < array_length(role_stages, 1))
);

CREATE TABLE approval_actions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id         UUID NOT NULL REFERENCES approval_requests (id),
    action_type        VARCHAR(24) NOT NULL,
    actor_member_id    UUID,
    delegate_member_id UUID,
    stage_index        INT NOT NULL,
    detail             VARCHAR(64),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_aa_type CHECK (action_type IN ('APPROVED', 'REJECTED', 'DELEGATED', 'ESCALATED',
                                                  'ESCALATION_BLOCKED', 'CANCELLED')),
    CONSTRAINT uq_aa_request_type_stage UNIQUE (request_id, action_type, stage_index)
);

CREATE INDEX idx_approval_requests_company_status ON approval_requests (company_id, status);
CREATE INDEX idx_approval_requests_site ON approval_requests (site_id) WHERE site_id IS NOT NULL;
CREATE INDEX idx_approval_requests_due ON approval_requests (escalation_due_at)
    WHERE status = 'PENDING' AND escalation_due_at IS NOT NULL;
CREATE INDEX idx_approval_actions_request ON approval_actions (request_id);

-- Gated orders hold no delivery slot while approval pends (capacity is released at the
-- gate and re-acquired on approval), so the lock reference must be nullable. Every B2C
-- order still assigns it at creation — the column keeps its meaning, just not the
-- NOT NULL constraint.
ALTER TABLE orders ALTER COLUMN delivery_slot_lock_id DROP NOT NULL;
