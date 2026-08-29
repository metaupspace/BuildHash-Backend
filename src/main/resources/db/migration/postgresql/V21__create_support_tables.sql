CREATE TABLE support_tickets (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users (id),
    category    VARCHAR(50) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    subject     VARCHAR(255) NOT NULL,
    sla_due_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_support_ticket_status CHECK (status IN ('OPEN', 'ESCALATED', 'RESOLVED', 'CLOSED'))
);

CREATE INDEX idx_support_tickets_user ON support_tickets (user_id);
CREATE INDEX idx_support_tickets_sla ON support_tickets (sla_due_at) WHERE status IN ('OPEN', 'ESCALATED');

CREATE TABLE support_ticket_messages (
    id          UUID PRIMARY KEY,
    ticket_id   UUID NOT NULL REFERENCES support_tickets (id) ON DELETE CASCADE,
    sender_role VARCHAR(20) NOT NULL,
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_support_message_sender CHECK (sender_role IN ('CUSTOMER', 'AGENT'))
);

CREATE INDEX idx_support_ticket_messages_ticket ON support_ticket_messages (ticket_id);
