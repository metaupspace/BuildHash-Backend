-- 9-E: monthly B2B statements.
--
-- statements: one row per generation attempt-series per (company, period, version).
-- Accounting source is the persisted order-line monetary columns; invoices certify
-- document readiness only (they carry no amounts), so missing/not-READY invoices are
-- recorded as discrepancies, never as exclusions or generation failures.
--
-- Lifecycle mirrors the proven invoice machine: PENDING/GENERATING/READY/DLQ_RETRY,
-- attemptCount + stale-GENERATING reclaim, number allocated only after both artifacts
-- are stored. READY rows are immutable — regeneration is a new row (new version, new
-- statement number); the UNIQUE(company_id, period_start, period_end, version) is the
-- multi-instance backstop and UNIQUE(statement_number) keeps every artifact distinctly
-- referenceable. pdf/xlsx_size_bytes let the email sweep reject oversized sends BEFORE
-- loading artifact bytes.
--
-- statement_sequences: per-company per-period counters, separate from gst_sequences
-- (locked 9-E decision). Row-level locked allocation; failed generations never consume
-- a number. NOT gapless by design.
--
-- Deletion: company delete cascades statements and their sequence rows (the company's
-- own artifacts); no FK touches orders, invoices, gst_notes or payments.

CREATE TABLE statements (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id          UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    period_start        TIMESTAMPTZ NOT NULL,
    period_end          TIMESTAMPTZ NOT NULL,
    period_key          VARCHAR(7) NOT NULL,
    status              VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    version             INT NOT NULL,
    statement_number    VARCHAR(24),
    pdf_storage_key     VARCHAR(255),
    xlsx_storage_key    VARCHAR(255),
    pdf_size_bytes      BIGINT,
    xlsx_size_bytes     BIGINT,
    generated_at        TIMESTAMPTZ,
    attempt_count       INT NOT NULL DEFAULT 0,
    email_status        VARCHAR(8) NOT NULL DEFAULT 'NONE',
    emailed_at          TIMESTAMPTZ,
    email_attempt_count INT NOT NULL DEFAULT 0,
    order_count         INT,
    gross_total         NUMERIC(12, 2),
    tax_total           NUMERIC(12, 2),
    net_total           NUMERIC(12, 2),
    credit_total        NUMERIC(12, 2),
    due_total           NUMERIC(12, 2),
    discrepancies       JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_statements_status CHECK (status IN ('PENDING', 'GENERATING', 'READY', 'DLQ_RETRY')),
    CONSTRAINT chk_statements_email CHECK (email_status IN ('NONE', 'SENT', 'FAILED', 'SKIPPED')),
    CONSTRAINT chk_statements_period CHECK (period_end > period_start),
    CONSTRAINT chk_statements_version CHECK (version >= 1),
    CONSTRAINT uq_statements_company_period_version UNIQUE (company_id, period_start, period_end, version),
    CONSTRAINT uq_statements_company_number UNIQUE (company_id, statement_number)
);

CREATE INDEX idx_statements_company_period ON statements (company_id, period_start DESC);
CREATE INDEX idx_statements_claim ON statements (status, attempt_count, updated_at);
CREATE INDEX idx_statements_email ON statements (email_status, email_attempt_count)
    WHERE status = 'READY' AND email_status IN ('NONE', 'FAILED');

CREATE TABLE statement_sequences (
    company_id  UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    period_key  VARCHAR(7) NOT NULL,
    current_val BIGINT NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (company_id, period_key)
);
