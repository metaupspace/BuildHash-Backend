-- H4.4: Persist tax_rate_percent snapshot on order line items to guarantee immutable invoice GST
ALTER TABLE order_line_items ADD COLUMN tax_rate_percent NUMERIC(5,2);

-- H4.3: Support negotiated unit price overrides on cart line items (e.g. converted RFQ quotes)
ALTER TABLE cart_line_items ADD COLUMN unit_price_override NUMERIC(12,2);
