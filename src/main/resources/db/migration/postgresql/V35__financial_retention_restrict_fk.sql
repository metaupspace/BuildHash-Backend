-- H4.6: Replace ON DELETE CASCADE with ON DELETE RESTRICT for statutory financial history

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_order_id_fkey;
ALTER TABLE payments ADD CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT;

ALTER TABLE invoices DROP CONSTRAINT IF EXISTS invoices_order_id_fkey;
ALTER TABLE invoices ADD CONSTRAINT fk_invoices_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT;

ALTER TABLE returns DROP CONSTRAINT IF EXISTS returns_order_id_fkey;
ALTER TABLE returns ADD CONSTRAINT fk_returns_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT;

ALTER TABLE refunds DROP CONSTRAINT IF EXISTS refunds_return_id_fkey;
ALTER TABLE refunds ADD CONSTRAINT fk_refunds_return FOREIGN KEY (return_id) REFERENCES returns(id) ON DELETE RESTRICT;

ALTER TABLE gst_notes DROP CONSTRAINT IF EXISTS gst_notes_return_id_fkey;
ALTER TABLE gst_notes ADD CONSTRAINT fk_gst_notes_return FOREIGN KEY (return_id) REFERENCES returns(id) ON DELETE RESTRICT;

ALTER TABLE statements DROP CONSTRAINT IF EXISTS statements_company_id_fkey;
ALTER TABLE statements ADD CONSTRAINT fk_statements_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE RESTRICT;
