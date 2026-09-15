CREATE TABLE webhook_destinations (
    id UUID PRIMARY KEY,
    endpoint_id UUID NOT NULL,
    url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT webhook_destinations_endpoint_fk
        FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoints(id) ON DELETE NO ACTION,
    CONSTRAINT webhook_destinations_endpoint_url_key UNIQUE (endpoint_id, url)
);

CREATE INDEX webhook_destinations_endpoint_created_at_id_desc_idx
    ON webhook_destinations (endpoint_id, created_at DESC, id DESC);
