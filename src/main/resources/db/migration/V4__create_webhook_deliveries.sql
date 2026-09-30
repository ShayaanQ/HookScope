CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    destination_id UUID NOT NULL,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('INITIAL', 'REPLAY')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ NULL,
    CONSTRAINT webhook_deliveries_event_fk FOREIGN KEY (event_id) REFERENCES webhook_events(id) ON DELETE NO ACTION,
    CONSTRAINT webhook_deliveries_destination_fk FOREIGN KEY (destination_id) REFERENCES webhook_destinations(id) ON DELETE NO ACTION,
    CONSTRAINT webhook_deliveries_completion_ck CHECK ((status = 'PENDING' AND completed_at IS NULL) OR (status IN ('SUCCEEDED','FAILED') AND completed_at IS NOT NULL))
);

CREATE INDEX webhook_deliveries_event_created_at_id_desc_idx ON webhook_deliveries (event_id, created_at DESC, id DESC);

CREATE TABLE webhook_delivery_attempts (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL CHECK (attempt_number >= 1),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('SUCCEEDED', 'FAILED')),
    http_status INTEGER NULL CHECK (http_status IS NULL OR (http_status BETWEEN 100 AND 599)),
    error_code VARCHAR(32) NULL CHECK (error_code IS NULL OR error_code IN ('TIMEOUT','CONNECT_ERROR','TLS_ERROR','IO_ERROR','INTERRUPTED','UNEXPECTED_ERROR')),
    duration_ms BIGINT NOT NULL CHECK (duration_ms >= 0),
    CONSTRAINT webhook_delivery_attempts_delivery_fk FOREIGN KEY (delivery_id) REFERENCES webhook_deliveries(id) ON DELETE NO ACTION,
    CONSTRAINT webhook_delivery_attempts_timestamps_ck CHECK (completed_at >= started_at),
    CONSTRAINT webhook_delivery_attempts_result_ck CHECK ((outcome = 'SUCCEEDED' AND http_status BETWEEN 200 AND 299 AND error_code IS NULL) OR (outcome = 'FAILED' AND ((http_status IS NOT NULL AND (http_status < 200 OR http_status > 299) AND error_code IS NULL) OR (http_status IS NULL AND error_code IS NOT NULL))))
);

ALTER TABLE webhook_delivery_attempts ADD CONSTRAINT webhook_delivery_attempts_delivery_number_key UNIQUE (delivery_id, attempt_number);
