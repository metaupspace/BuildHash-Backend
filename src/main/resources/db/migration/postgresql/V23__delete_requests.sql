-- DPDP account-deletion requests (PLAN_PHASE8 decision 9, Checkpoint C).
-- The partial unique index is the concurrency backstop: two racing POST /users/me/delete-request
-- both pass the service-level existence check, the second insert violates this index and the
-- service translates DataIntegrityViolationException into the same 409 — never a 500, never
-- two pending requests (ContractPrice overlap precedent).

CREATE TABLE delete_requests (
    id                     UUID PRIMARY KEY,
    user_id                UUID NOT NULL,
    requested_at           TIMESTAMPTZ NOT NULL,
    deletion_scheduled_at  TIMESTAMPTZ NOT NULL,
    processed_at           TIMESTAMPTZ,
    status                 VARCHAR(16) NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_delete_requests_pending_user
    ON delete_requests (user_id) WHERE status = 'PENDING';

CREATE INDEX idx_delete_requests_due
    ON delete_requests (deletion_scheduled_at) WHERE status = 'PENDING';
