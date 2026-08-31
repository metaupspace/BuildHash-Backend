-- Checkpoint 9-C: PO attachments + streaming XLSX bulk import + draft conversion.
--
-- po_attachments: one PO document per order (UNIQUE order_id). The row is a durable
-- claim following the refund-gateway discipline (48aeabe): PENDING is written in a
-- short transaction, ObjectStorage.store runs outside any transaction, then a
-- conditional PENDING->STORED update finalizes. A failed store keeps the claim so
-- an explicit retry can reuse the same stable id/key. No cascade into orders —
-- orders are financial-record RETAIN class.
--
-- po_imports: one row per bulk upload attempt. UNIQUE(company_id, idempotency_key)
-- is the final idempotency backstop (no content hash, no expiry). FAILED_STRUCTURE
-- imports persist too — a structural failure consumes the key, so a corrected file
-- needs a new key.
--
-- po_import_rows: exactly one row per nonblank source row, including INVALID ones —
-- product_slug/quantity are NULLable so malformed values stay representable, never
-- truncated or dropped. import->rows cascades; nothing cascades into orders.

CREATE TABLE po_attachments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID NOT NULL UNIQUE REFERENCES orders (id),
    storage_key  TEXT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    byte_size    INTEGER NOT NULL CHECK (byte_size BETWEEN 1 AND 2097152),
    uploaded_by  UUID NOT NULL REFERENCES users (id),
    status       VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_po_attachments_status CHECK (status IN ('PENDING', 'STORED'))
);

CREATE TABLE po_imports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies (id),
    idempotency_key VARCHAR(100) NOT NULL,
    uploaded_by     UUID NOT NULL REFERENCES users (id),
    status          VARCHAR(20) NOT NULL,
    total_rows      INT NOT NULL DEFAULT 0,
    valid_rows      INT NOT NULL DEFAULT 0,
    invalid_rows    INT NOT NULL DEFAULT 0,
    draft_cart_id   UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_po_imports_status CHECK (status IN ('RECEIVED', 'PARSED', 'REVIEW', 'CONVERTED', 'FAILED_STRUCTURE')),
    CONSTRAINT uq_po_imports_company_key UNIQUE (company_id, idempotency_key)
);

CREATE TABLE po_import_rows (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    import_id    UUID NOT NULL REFERENCES po_imports (id) ON DELETE CASCADE,
    row_index    INT NOT NULL,
    product_slug TEXT,
    quantity     INT,
    status       VARCHAR(10) NOT NULL,
    error_code   VARCHAR(30),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_po_import_rows_status CHECK (status IN ('VALID', 'INVALID')),
    CONSTRAINT uq_po_import_rows UNIQUE (import_id, row_index)
);

CREATE INDEX idx_po_import_rows_import ON po_import_rows (import_id);
