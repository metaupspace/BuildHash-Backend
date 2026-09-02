-- H5.3: Drop dead plaintext user unique indexes left behind after V22 PII blind index migration

DROP INDEX IF EXISTS uq_users_phone;
DROP INDEX IF EXISTS uq_users_email;
DROP INDEX IF EXISTS uq_users_google_id;
