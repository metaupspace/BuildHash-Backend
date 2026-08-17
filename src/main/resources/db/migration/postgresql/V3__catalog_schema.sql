CREATE TABLE categories (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(255) NOT NULL,
    slug              VARCHAR(255) NOT NULL,
    parent_id         UUID REFERENCES categories (id) ON DELETE SET NULL,
    attribute_schema  JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE UNIQUE INDEX uq_categories_slug ON categories (slug);

CREATE TABLE products (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(255) NOT NULL,
    slug              VARCHAR(255) NOT NULL,
    category_id       UUID NOT NULL REFERENCES categories (id),
    brand             VARCHAR(255),
    hsn_code          VARCHAR(8),
    attributes        JSONB NOT NULL DEFAULT '{}'::jsonb,
    images            JSONB NOT NULL DEFAULT '[]'::jsonb,
    stock             JSONB NOT NULL DEFAULT '[]'::jsonb,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_product_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DISCONTINUED'))
);

CREATE INDEX idx_products_category_id ON products (category_id);
