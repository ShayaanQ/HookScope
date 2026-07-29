CREATE TABLE webhook_events (
    id UUID PRIMARY KEY,
    endpoint_id UUID NOT NULL,
    method VARCHAR(10) NOT NULL,
    headers JSONB NOT NULL,
    query_parameters JSONB NOT NULL,
    content_type VARCHAR(255),
    body BYTEA NOT NULL,
    body_size BIGINT NOT NULL,
    body_sha256 CHAR(64) NOT NULL,
    source_ip INET NOT NULL,
    path VARCHAR(1024) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT webhook_events_endpoint_id_fkey
        FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoints(id) ON DELETE NO ACTION,
    CONSTRAINT webhook_events_body_size_nonnegative CHECK (body_size >= 0),
    CONSTRAINT webhook_events_body_sha256_format CHECK (body_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX webhook_events_endpoint_id_received_at_id_desc_idx
    ON webhook_events (endpoint_id, received_at DESC, id DESC);
