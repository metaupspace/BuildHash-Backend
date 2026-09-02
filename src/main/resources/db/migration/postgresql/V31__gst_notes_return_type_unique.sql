-- H1.1 backstop (not the primary concurrency mechanism — that is the RETURN -> REFUND
-- row-lock discipline in RefundWebhookServiceImpl/RefundServiceImpl): one return can
-- never carry two GST notes of the same type (e.g. two CREDIT notes for one refund).
ALTER TABLE gst_notes ADD CONSTRAINT uq_gst_notes_return_type UNIQUE (return_id, note_type);
