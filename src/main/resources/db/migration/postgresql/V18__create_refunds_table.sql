CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    return_id UUID NOT NULL REFERENCES returns(id) ON DELETE CASCADE,
    payment_transaction_id VARCHAR(100) NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    gateway_refund_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_refunds_gateway_refund_id UNIQUE (gateway_refund_id)
);

CREATE INDEX idx_refunds_return ON refunds(return_id);
