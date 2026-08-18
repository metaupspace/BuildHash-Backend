CREATE TABLE search_queries (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID REFERENCES users (id),
    query_text    VARCHAR(255) NOT NULL,
    lang          VARCHAR(10),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_search_queries_user_id ON search_queries (user_id);
CREATE INDEX idx_search_queries_created_at ON search_queries (created_at);
