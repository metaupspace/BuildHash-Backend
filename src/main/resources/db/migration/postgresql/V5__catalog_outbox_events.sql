CREATE TABLE catalog_outbox_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id    UUID NOT NULL REFERENCES products (id),
    event_type    VARCHAR(50) NOT NULL,
    payload       TEXT NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'PROCESSED'))
);

CREATE INDEX idx_catalog_outbox_events_status ON catalog_outbox_events (status);
