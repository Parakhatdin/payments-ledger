-- Inbox of Stripe events. event_id UNIQUE = first layer of idempotency: a redelivered event is stored once.
-- The HTTP request only inserts a row (fast 200 to Stripe); a background worker processes it.
CREATE TABLE webhook_events (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id     VARCHAR(100) NOT NULL,
    type         VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL CHECK (status IN ('RECEIVED', 'PROCESSED', 'FAILED')),
    attempts     INT          NOT NULL DEFAULT 0,
    last_error   TEXT,
    received_at  TIMESTAMPTZ  NOT NULL,
    processed_at TIMESTAMPTZ,
    CONSTRAINT uq_webhook_events_event_id UNIQUE (event_id)
);

-- The worker only ever looks for RECEIVED rows; a partial index keeps that lookup small.
CREATE INDEX ix_webhook_events_pending ON webhook_events (id) WHERE status = 'RECEIVED';
