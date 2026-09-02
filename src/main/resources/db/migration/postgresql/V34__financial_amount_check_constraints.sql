-- H4.5: Non-negative CHECK constraints on all financial amount columns

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_total_amount CHECK (total_amount >= 0);

ALTER TABLE order_line_items
    ADD CONSTRAINT chk_order_line_items_unit_price CHECK (unit_price >= 0),
    ADD CONSTRAINT chk_order_line_items_tax_amount CHECK (tax_amount >= 0),
    ADD CONSTRAINT chk_order_line_items_line_total CHECK (line_total IS NULL OR line_total >= 0);

ALTER TABLE payments
    ADD CONSTRAINT chk_payments_amount CHECK (amount >= 0);

ALTER TABLE refunds
    ADD CONSTRAINT chk_refunds_amount CHECK (amount >= 0);

ALTER TABLE return_line_items
    ADD CONSTRAINT chk_return_line_items_refund_amount CHECK (refund_amount >= 0);

ALTER TABLE gst_notes
    ADD CONSTRAINT chk_gst_notes_amount CHECK (amount >= 0);

ALTER TABLE coupons
    ADD CONSTRAINT chk_coupons_discount_value CHECK (discount_value >= 0),
    ADD CONSTRAINT chk_coupons_min_order_value CHECK (min_order_value IS NULL OR min_order_value >= 0);

ALTER TABLE margin_rules
    ADD CONSTRAINT chk_margin_rules_cost_price CHECK (cost_price IS NULL OR cost_price >= 0),
    ADD CONSTRAINT chk_margin_rules_floor_price CHECK (floor_price IS NULL OR floor_price >= 0);

ALTER TABLE statements
    ADD CONSTRAINT chk_statements_gross_total CHECK (gross_total IS NULL OR gross_total >= 0),
    ADD CONSTRAINT chk_statements_tax_total CHECK (tax_total IS NULL OR tax_total >= 0),
    ADD CONSTRAINT chk_statements_net_total CHECK (net_total IS NULL OR net_total >= 0),
    ADD CONSTRAINT chk_statements_credit_total CHECK (credit_total IS NULL OR credit_total >= 0),
    ADD CONSTRAINT chk_statements_due_total CHECK (due_total IS NULL OR due_total >= 0);
