ALTER TABLE coupons ADD COLUMN min_order_value NUMERIC(12,2);

CREATE TABLE carts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    project_id UUID,
    applied_cart_coupon VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_carts_user_project ON carts(user_id, COALESCE(project_id, '00000000-0000-0000-0000-000000000000'));

CREATE TABLE cart_line_items (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    applied_item_coupon VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cart_line_item_product UNIQUE (cart_id, product_id)
);

CREATE INDEX idx_cart_line_items_cart ON cart_line_items(cart_id);
