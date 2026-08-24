-- Hot-path indexes (audit findings): order history seq-scanned orders; the public
-- catalog listing keyset-paginates products with ORDER BY created_at, id + status filter.
CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_products_status_created_id ON products (status, created_at, id);

-- Exact line totals on order line items: unit_price is GST-inclusive (final/quantity
-- rounded), so SUM(unit_price * quantity) drifts from the charged total by paise.
-- line_total stores what the customer actually paid for the line, GST included.
ALTER TABLE order_line_items ADD COLUMN line_total NUMERIC(12,2);
UPDATE order_line_items SET line_total = ROUND(unit_price * quantity, 2) WHERE line_total IS NULL;
ALTER TABLE order_line_items ALTER COLUMN line_total SET NOT NULL;
