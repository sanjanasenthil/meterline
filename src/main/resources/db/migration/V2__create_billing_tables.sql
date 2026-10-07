CREATE TABLE billing_periods (
    period_id TEXT PRIMARY KEY,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL,
    closed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT billing_periods_status_check
        CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT billing_periods_range_check
        CHECK (period_start < period_end),
    CONSTRAINT billing_periods_unique_range
        UNIQUE (period_start, period_end)
);

CREATE TABLE usage_aggregates (
    aggregate_id TEXT PRIMARY KEY,
    customer_id TEXT NOT NULL,
    meter_id TEXT NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    quantity_units BIGINT NOT NULL,
    source_event_count BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT usage_aggregates_range_check
        CHECK (period_start < period_end),
    CONSTRAINT usage_aggregates_unique_scope
        UNIQUE (customer_id, meter_id, period_start, period_end)
);

CREATE TABLE invoices (
    invoice_id TEXT PRIMARY KEY,
    customer_id TEXT NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL,
    total_cents BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT invoices_status_check
        CHECK (status IN ('DRAFT', 'ISSUED')),
    CONSTRAINT invoices_range_check
        CHECK (period_start < period_end),
    CONSTRAINT invoices_unique_scope
        UNIQUE (customer_id, period_start, period_end)
);

CREATE TABLE invoice_lines (
    line_id TEXT PRIMARY KEY,
    invoice_id TEXT NOT NULL REFERENCES invoices(invoice_id) ON DELETE CASCADE,
    customer_id TEXT NOT NULL,
    meter_id TEXT NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    line_type TEXT NOT NULL,
    quantity_units BIGINT NOT NULL,
    amount_cents BIGINT NOT NULL,
    rate_millionths_of_cent BIGINT NOT NULL,
    source_aggregate_id TEXT NULL REFERENCES usage_aggregates(aggregate_id),
    adjustment_for_event_id TEXT NULL REFERENCES raw_usage_events(event_id),
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT invoice_lines_type_check
        CHECK (line_type IN ('USAGE', 'ADJUSTMENT'))
);

CREATE INDEX usage_aggregates_period_idx
    ON usage_aggregates (period_start, period_end);

CREATE INDEX invoices_period_idx
    ON invoices (period_start, period_end);

CREATE INDEX invoice_lines_invoice_idx
    ON invoice_lines (invoice_id);

CREATE INDEX invoice_lines_adjustment_event_idx
    ON invoice_lines (adjustment_for_event_id);
