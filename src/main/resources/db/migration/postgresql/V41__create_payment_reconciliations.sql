-- H9.1: Durable payment reconciliation work items for automated and manual financial tracking
CREATE TABLE payment_reconciliations (
    id                  UUID PRIMARY KEY,
    order_id            UUID NOT NULL REFERENCES orders(id),
    payment_id          UUID NOT NULL REFERENCES payments(id),
    transaction_id      VARCHAR(100),
    amount              NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    reconciliation_type VARCHAR(50) NOT NULL,
    status              VARCHAR(50) NOT NULL,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_reconciliations_order_type UNIQUE (order_id, reconciliation_type)
);

CREATE INDEX idx_payment_reconciliations_status ON payment_reconciliations(status);
