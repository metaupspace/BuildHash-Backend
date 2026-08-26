CREATE TABLE returns (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(50) NOT NULL,
    reason VARCHAR(50) NOT NULL,
    photo_keys JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_returns_order ON returns(order_id);
CREATE INDEX idx_returns_user ON returns(user_id);

CREATE TABLE return_line_items (
    id UUID PRIMARY KEY,
    return_id UUID NOT NULL REFERENCES returns(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    quantity_requested INT NOT NULL CHECK (quantity_requested > 0),
    refund_amount NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_return_line_items_return ON return_line_items(return_id);

ALTER TABLE categories ADD COLUMN return_window_days INT NOT NULL DEFAULT 7;
