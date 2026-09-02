-- H5.1: Missing operational indexes for verified hot paths

CREATE INDEX idx_notification_logs_user ON notification_logs (user_id);
CREATE INDEX idx_orders_site_id ON orders (site_id) WHERE site_id IS NOT NULL;
CREATE INDEX idx_orders_address_id ON orders (address_id);
CREATE INDEX idx_idempotency_keys_created ON idempotency_keys (created_at);
CREATE INDEX idx_reviews_user_id ON reviews (user_id);
CREATE INDEX idx_questions_user_id ON questions (user_id);
CREATE INDEX idx_answers_user_id ON answers (user_id);
