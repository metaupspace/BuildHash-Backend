-- Checkpoint 9-B: RFQ lifecycle with vendor routing, controlled quotes and
-- draft conversion.
--
-- vendors / vendor_categories / rfqs / rfq_items / rfq_quotes / rfq_routes.
--
-- Deletion semantics (mirrors 9-A doctrine):
--  - RFQ-derived rows (items, quotes, routes) cascade with the RFQ;
--  - vendor references from rfq_quotes and rfq_routes use default RESTRICT —
--    historical routing and quotes must never disappear with a vendor, and
--    ordinary vendor PATCHes never touch them (routes are a creation-time
--    snapshot, never recalculated);
--  - no FK reaches orders or any financial record from here.
--
-- rfq_quotes UNIQUE (rfq_id, vendor_id) is the final duplicate protection: the
-- application checks first for a friendly 409, the constraint closes the race.

CREATE TABLE vendors (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Vendor category mapping reuses the existing catalog categories: a vendor is
-- routed to an RFQ when it matches ANY category represented by the RFQ items.
CREATE TABLE vendor_categories (
    vendor_id   UUID NOT NULL REFERENCES vendors (id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories (id),
    PRIMARY KEY (vendor_id, category_id)
);

CREATE INDEX idx_vendor_categories_category ON vendor_categories (category_id);

CREATE TABLE rfqs (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id         UUID NOT NULL REFERENCES companies (id),
    created_by_user_id UUID NOT NULL REFERENCES users (id),
    status             VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    expires_at         TIMESTAMPTZ NOT NULL,
    notes              TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_rfqs_status CHECK (status IN ('OPEN', 'EXPIRED', 'CONVERTED', 'CANCELLED'))
);

-- Sweeper coverage: expireOpenBefore() only ever touches OPEN rows.
CREATE INDEX idx_rfqs_open_expiry ON rfqs (expires_at) WHERE status = 'OPEN';

CREATE TABLE rfq_items (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rfq_id     UUID NOT NULL REFERENCES rfqs (id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products (id),
    quantity   INTEGER NOT NULL CHECK (quantity > 0)
);

CREATE INDEX idx_rfq_items_rfq ON rfq_items (rfq_id);

CREATE TABLE rfq_quotes (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rfq_id       UUID NOT NULL REFERENCES rfqs (id) ON DELETE CASCADE,
    vendor_id    UUID NOT NULL REFERENCES vendors (id),
    total_amount NUMERIC(12,2) NOT NULL CHECK (total_amount > 0),
    valid_until  TIMESTAMPTZ NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_rfq_quotes_status CHECK (status IN ('SUBMITTED')),
    CONSTRAINT uq_rfq_quotes_rfq_vendor UNIQUE (rfq_id, vendor_id)
);

CREATE INDEX idx_rfq_quotes_rfq ON rfq_quotes (rfq_id);

-- Creation-time routing snapshot: distinct (rfq, vendor) matches. PK gives set
-- semantics; the vendor FK has no cascade so history cannot be destroyed.
CREATE TABLE rfq_routes (
    rfq_id   UUID NOT NULL REFERENCES rfqs (id) ON DELETE CASCADE,
    vendor_id UUID NOT NULL REFERENCES vendors (id),
    PRIMARY KEY (rfq_id, vendor_id)
);
