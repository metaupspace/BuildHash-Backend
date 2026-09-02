-- H5.2: Catalog outbox retry metadata and terminal FAILED status

ALTER TABLE catalog_outbox_events
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN last_attempt_at TIMESTAMPTZ,
    ADD COLUMN error_message TEXT;

ALTER TABLE catalog_outbox_events DROP CONSTRAINT chk_outbox_status;
ALTER TABLE catalog_outbox_events ADD CONSTRAINT chk_outbox_status
    CHECK (status IN ('PENDING', 'PUBLISHED', 'PROCESSED', 'FAILED'));
