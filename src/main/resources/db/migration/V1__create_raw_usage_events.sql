CREATE TABLE raw_usage_events (
    event_id TEXT PRIMARY KEY,
    customer_id TEXT NOT NULL,
    meter_id TEXT NOT NULL,
    source TEXT NOT NULL,
    source_event_key TEXT NOT NULL,
    quantity_units BIGINT NOT NULL,
    event_timestamp TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    event_type TEXT NOT NULL,
    adjustment_for_event_id TEXT NULL REFERENCES raw_usage_events(event_id),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT raw_usage_events_type_check
        CHECK (event_type IN ('USAGE', 'ADJUSTMENT')),
    CONSTRAINT raw_usage_events_usage_quantity_check
        CHECK (
            (event_type = 'USAGE' AND quantity_units > 0 AND adjustment_for_event_id IS NULL)
            OR
            (event_type = 'ADJUSTMENT' AND quantity_units <> 0)
        )
);

CREATE UNIQUE INDEX raw_usage_events_business_action_idx
    ON raw_usage_events (customer_id, meter_id, source, source_event_key, event_timestamp, event_type);

CREATE INDEX raw_usage_events_customer_meter_time_idx
    ON raw_usage_events (customer_id, meter_id, event_timestamp);

CREATE INDEX raw_usage_events_event_timestamp_idx
    ON raw_usage_events (event_timestamp);
