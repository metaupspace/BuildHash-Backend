CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone              VARCHAR(15),
    email              VARCHAR(255),
    google_id          VARCHAR(255),
    name               VARCHAR(255),
    business_name      VARCHAR(255),
    gst_number         VARCHAR(15),
    gstin_status       VARCHAR(20),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_gstin_status_values
        CHECK (gstin_status IS NULL OR gstin_status IN ('PENDING', 'VERIFIED', 'INVALID')),
    CONSTRAINT chk_gstin_status_requires_gst_number
        CHECK (gstin_status IS NULL OR gst_number IS NOT NULL)
);

CREATE UNIQUE INDEX uq_users_phone     ON users (phone)     WHERE phone IS NOT NULL;
CREATE UNIQUE INDEX uq_users_email     ON users (email)     WHERE email IS NOT NULL;
CREATE UNIQUE INDEX uq_users_google_id ON users (google_id) WHERE google_id IS NOT NULL;

CREATE TABLE devices (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    refresh_token_hash  VARCHAR(64) NOT NULL,
    device_fingerprint  VARCHAR(255),
    last_seen_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at          TIMESTAMPTZ
);

CREATE INDEX idx_devices_user_id ON devices (user_id);

CREATE TABLE login_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    event_type          VARCHAR(20) NOT NULL,
    ip_address          VARCHAR(45),
    device_fingerprint  VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_login_event_type CHECK (event_type IN ('OTP', 'GOOGLE'))
);

CREATE INDEX idx_login_events_user_id_created_at ON login_events (user_id, created_at DESC);

CREATE TABLE hsn_gst_rates (
    hsn_code          VARCHAR(8) PRIMARY KEY,
    description       VARCHAR(255) NOT NULL,
    gst_rate_percent  NUMERIC(5,2) NOT NULL,
    category          VARCHAR(50) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
