CREATE TABLE reviews (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id         UUID NOT NULL REFERENCES products (id),
    user_id            UUID NOT NULL REFERENCES users (id),
    rating             INTEGER NOT NULL,
    comment            TEXT,
    status             VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    verified_purchase  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_review_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_reviews_product_id ON reviews (product_id);

-- Questions are not moderated in Phase 1 (unlike Review/Answer) — see PROGRESS.md Wave 2.
CREATE TABLE questions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id    UUID NOT NULL REFERENCES products (id),
    user_id       UUID NOT NULL REFERENCES users (id),
    body          TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_questions_product_id ON questions (product_id);

CREATE TABLE answers (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id   UUID NOT NULL REFERENCES questions (id),
    user_id       UUID NOT NULL REFERENCES users (id),
    body          TEXT NOT NULL,
    source        VARCHAR(20) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_answer_source CHECK (source IN ('VENDOR', 'STAFF', 'CUSTOMER')),
    CONSTRAINT chk_answer_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_answers_question_id ON answers (question_id);

CREATE TABLE wishlist_entries (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users (id),
    product_id    UUID NOT NULL REFERENCES products (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_wishlist_entries_user_product ON wishlist_entries (user_id, product_id);

CREATE TABLE notify_me_subscriptions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id    UUID NOT NULL REFERENCES products (id),
    user_id       UUID NOT NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_notify_me_subscriptions_product_user ON notify_me_subscriptions (product_id, user_id);
