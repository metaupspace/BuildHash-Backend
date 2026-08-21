CREATE TABLE product_base_prices (
    product_id  UUID PRIMARY KEY REFERENCES products (id),
    price       NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE bulk_pricing_tiers (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id    UUID NOT NULL REFERENCES products (id),
    min_quantity  INTEGER NOT NULL CHECK (min_quantity > 0),
    unit_price    NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_bulk_pricing_tiers_product_min_qty
    ON bulk_pricing_tiers (product_id, min_quantity);
CREATE INDEX idx_bulk_pricing_tiers_product_id ON bulk_pricing_tiers (product_id);

-- btree_gist backs the exclusion constraint below (equality terms in an EXCLUDE USING
-- gist need btree_gist's operator class support). Bundled in the official postgres
-- image's contrib modules, so no extra image/package is required.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE contract_pricing (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users (id),
    product_id       UUID NOT NULL REFERENCES products (id),
    unit_price       NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    effective_from   TIMESTAMPTZ NOT NULL DEFAULT now(),
    effective_to     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_contract_pricing_effective_range
        CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE INDEX idx_contract_pricing_user_product ON contract_pricing (user_id, product_id);

-- DB-level backstop against a race between two concurrent inserts that both pass the
-- application-level overlap check in ContractPriceRepositoryAdapter#save. tstzrange
-- treats a NULL effective_to as unbounded (open-ended window), matching the domain's
-- "effective_to IS NULL = still active" semantics. See PROGRESS.md for the full
-- concurrency-safety rationale.
ALTER TABLE contract_pricing
    ADD CONSTRAINT excl_contract_pricing_no_overlap
    EXCLUDE USING gist (
        user_id WITH =,
        product_id WITH =,
        tstzrange(effective_from, effective_to) WITH &&
    );

CREATE TABLE coupons (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                  VARCHAR(50) NOT NULL,
    discount_type         VARCHAR(10) NOT NULL CHECK (discount_type IN ('PERCENT', 'FLAT')),
    discount_value        NUMERIC(12,2) NOT NULL,
    expires_at            TIMESTAMPTZ NOT NULL,
    max_uses_per_user     INTEGER,
    eligible_category_ids JSONB NOT NULL DEFAULT '[]',
    stackable             BOOLEAN NOT NULL DEFAULT FALSE,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_coupons_code ON coupons (UPPER(code));

-- Read-only in Phase 2 — no code path inserts into this table yet. Created now so
-- Phase 3 (Order) doesn't need a migration just to add it.
CREATE TABLE coupon_redemptions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coupon_id     UUID NOT NULL REFERENCES coupons (id),
    user_id       UUID NOT NULL REFERENCES users (id),
    order_id      UUID,
    redeemed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_coupon_redemptions_coupon_user ON coupon_redemptions (coupon_id, user_id);

CREATE TABLE margin_rules (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id     UUID REFERENCES products (id),
    category_id    UUID REFERENCES categories (id),
    cost_price     NUMERIC(12,2),
    floor_percent  NUMERIC(5,2),
    floor_price    NUMERIC(12,2),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_margin_rules_scope CHECK (
        (product_id IS NOT NULL AND category_id IS NULL) OR
        (product_id IS NULL AND category_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_margin_rules_product_id ON margin_rules (product_id) WHERE product_id IS NOT NULL;
CREATE UNIQUE INDEX uq_margin_rules_category_id ON margin_rules (category_id) WHERE category_id IS NOT NULL;
