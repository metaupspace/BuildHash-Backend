CREATE TABLE gst_sequences (
    sequence_type VARCHAR(50) PRIMARY KEY,
    fiscal_year VARCHAR(20) NOT NULL,
    prefix VARCHAR(20) NOT NULL,
    current_val BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO gst_sequences (sequence_type, fiscal_year, prefix, current_val, updated_at)
VALUES
    ('INVOICE', '2026-2027', 'INV-2627-', 0, CURRENT_TIMESTAMP),
    ('CREDIT_NOTE', '2026-2027', 'CRN-2627-', 0, CURRENT_TIMESTAMP),
    ('DEBIT_NOTE', '2026-2027', 'DBN-2627-', 0, CURRENT_TIMESTAMP)
ON CONFLICT (sequence_type) DO NOTHING;

CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    number VARCHAR(100),
    status VARCHAR(50) NOT NULL,
    storage_key VARCHAR(255),
    content_type VARCHAR(50) DEFAULT 'application/pdf',
    generated_at TIMESTAMP WITH TIME ZONE,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_invoices_number UNIQUE (number),
    CONSTRAINT uq_invoices_order_id UNIQUE (order_id)
);

CREATE INDEX idx_invoices_order ON invoices(order_id);
CREATE INDEX idx_invoices_status ON invoices(status);

CREATE TABLE gst_notes (
    id UUID PRIMARY KEY,
    return_id UUID NOT NULL REFERENCES returns(id) ON DELETE CASCADE,
    note_type VARCHAR(50) NOT NULL,
    number VARCHAR(100) NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_gst_notes_number UNIQUE (number)
);

CREATE INDEX idx_gst_notes_return ON gst_notes(return_id);
