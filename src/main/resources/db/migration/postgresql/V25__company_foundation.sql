-- Phase 9 / Checkpoint 9-A: B2B company foundation.
-- companies / company_sites / company_members / company_site_assignments /
-- company_contract_pricing, plus B2B tagging columns on orders and carts.
--
-- Deletion semantics are deliberate:
--  - company-derived data (members, assignments, sites, contract pricing) cascades —
--    it has no meaning without the company;
--  - orders.company_id/site_id have NO cascade — retained financial order data must
--    never disappear with a company, so a company with orders simply cannot be deleted
--    (FK RESTRICT-by-default blocks the delete instead of destroying history).
--
-- All added columns are nullable and only populated for new B2B rows: every existing
-- B2C row stays valid, and V14's cart uniqueness index is untouched (B2B_DRAFT carts
-- are a distinct cart_type in the same key).

CREATE TABLE companies (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              TEXT NOT NULL,
    gst_number        VARCHAR(20),
    statement_email   TEXT,
    business_timezone VARCHAR(40) NOT NULL DEFAULT 'Asia/Kolkata',
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_companies_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE company_sites (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    address_id  UUID REFERENCES addresses (id),
    active      BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_company_sites_name UNIQUE (company_id, name)
);

CREATE INDEX idx_company_sites_company ON company_sites (company_id);

CREATE TABLE company_members (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users (id),
    role        VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_company_members UNIQUE (company_id, user_id),
    CONSTRAINT chk_company_members_role CHECK (role IN ('OWNER', 'ADMIN', 'APPROVER', 'BUYER'))
);

-- Token-issuance lookup (AuthServiceImpl resolves memberships at login/refresh).
CREATE INDEX idx_company_members_user ON company_members (user_id);
CREATE INDEX idx_company_members_company ON company_members (company_id);

CREATE TABLE company_site_assignments (
    member_id UUID NOT NULL REFERENCES company_members (id) ON DELETE CASCADE,
    site_id   UUID NOT NULL REFERENCES company_sites (id) ON DELETE CASCADE,
    PRIMARY KEY (member_id, site_id)
);

-- Approver/site-scope resolution (9-D) and scope checks read this direction.
CREATE INDEX idx_company_site_assignments_site ON company_site_assignments (site_id);

-- Company-level contract pricing, additive tier above the existing user-level
-- contract_pricing (V7). Precedence lives in PricingCalculatorImpl.loadContext:
-- company contract -> user contract -> fallback. Same btree_gist requirement and same
-- overlap semantics as V7's excl_contract_pricing_no_overlap (already created in V7;
-- IF NOT EXISTS keeps this idempotent).
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE company_contract_pricing (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id     UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    product_id     UUID NOT NULL REFERENCES products (id),
    unit_price     NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    effective_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    effective_to   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_company_contract_pricing_effective_range
        CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE INDEX idx_company_contract_pricing_company_product ON company_contract_pricing (company_id, product_id);

ALTER TABLE company_contract_pricing
    ADD CONSTRAINT excl_company_contract_pricing_no_overlap
    EXCLUDE USING gist (
        company_id WITH =,
        product_id WITH =,
        tstzrange(effective_from, effective_to) WITH &&
    );

-- B2B order tagging. confirmed_at is stamped ONLY by Order.confirm() (payment webhook
-- path); creation never sets it. No cascade: a company deletion with retained orders
-- is blocked by the FK instead of cascading away financial data.
ALTER TABLE orders
    ADD COLUMN company_id   UUID REFERENCES companies (id),
    ADD COLUMN site_id      UUID REFERENCES company_sites (id),
    ADD COLUMN confirmed_at TIMESTAMPTZ;

-- Monthly-statement aggregation (9-E) reads company orders by confirmation time.
CREATE INDEX idx_orders_company_confirmed ON orders (company_id, confirmed_at)
    WHERE company_id IS NOT NULL;

-- Explicit B2B cart scoping (OQ-6): company = tenant scope, site = physical operating
-- scope, project_id keeps its existing cart/project semantics. No cascade — cart
-- lifecycle is independent of company lifecycle.
ALTER TABLE carts ADD COLUMN company_id UUID REFERENCES companies (id);

CREATE INDEX idx_carts_company ON carts (company_id) WHERE company_id IS NOT NULL;
