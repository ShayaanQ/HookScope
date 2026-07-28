CREATE TABLE webhook_endpoints (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    public_key VARCHAR(32) NOT NULL,
    CONSTRAINT webhook_endpoints_public_key_key UNIQUE (public_key),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX webhook_endpoints_created_at_id_desc_idx
    ON webhook_endpoints (created_at DESC, id DESC);
