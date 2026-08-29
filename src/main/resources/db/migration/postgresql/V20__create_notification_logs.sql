CREATE TABLE notification_logs (
    id               UUID PRIMARY KEY,
    user_id          UUID NOT NULL REFERENCES users (id),
    recipient_phone  VARCHAR(15) NOT NULL,
    channel          VARCHAR(20) NOT NULL,
    event_type       VARCHAR(50) NOT NULL,
    reference_id     UUID NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at          TIMESTAMPTZ,
    delivered_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_notification_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

-- reference_id is polymorphic (orderId/returnId/cartId) so no FK; this index backs the
-- (eventType, referenceId) idempotency-guard lookup. Deliberately NOT unique: a plain index
-- keeps the guard application-level, same as the OrderConfirmedInvoiceListener presence check.
CREATE INDEX idx_notification_logs_event_ref ON notification_logs (event_type, reference_id);
