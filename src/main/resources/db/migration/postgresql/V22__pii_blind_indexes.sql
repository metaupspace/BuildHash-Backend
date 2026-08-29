-- PII encryption groundwork (PLAN_PHASE8 Checkpoint A).
-- 1) Widen/retype PII columns to TEXT: AES-GCM ciphertext ("v1:" + base64(nonce||ct+tag))
--    is ~60+ chars for short inputs and lat/lng change storage kind entirely (Double -> ciphertext String).
-- 2) Blind-index columns for the three lookup-bearing identity fields; unique partial
--    indexes land on the idx columns (safe while empty). The old plaintext unique indexes
--    stay until V23 removes them post-backfill.

ALTER TABLE users ALTER COLUMN phone TYPE TEXT;
ALTER TABLE users ALTER COLUMN email TYPE TEXT;
ALTER TABLE users ALTER COLUMN google_id TYPE TEXT;
ALTER TABLE users ALTER COLUMN name TYPE TEXT;
ALTER TABLE users ALTER COLUMN business_name TYPE TEXT;
ALTER TABLE users ALTER COLUMN gst_number TYPE TEXT;

ALTER TABLE addresses ALTER COLUMN line1 TYPE TEXT;
ALTER TABLE addresses ALTER COLUMN line2 TYPE TEXT;
ALTER TABLE addresses ALTER COLUMN lat TYPE TEXT USING lat::text;
ALTER TABLE addresses ALTER COLUMN lng TYPE TEXT USING lng::text;

ALTER TABLE notification_logs ALTER COLUMN recipient_phone TYPE TEXT;

ALTER TABLE users ADD COLUMN phone_idx TEXT;
ALTER TABLE users ADD COLUMN email_idx TEXT;
ALTER TABLE users ADD COLUMN google_id_idx TEXT;

CREATE UNIQUE INDEX uq_users_phone_idx ON users (phone_idx) WHERE phone_idx IS NOT NULL;
CREATE UNIQUE INDEX uq_users_email_idx ON users (email_idx) WHERE email_idx IS NOT NULL;
CREATE UNIQUE INDEX uq_users_google_id_idx ON users (google_id_idx) WHERE google_id_idx IS NOT NULL;
