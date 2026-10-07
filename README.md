# Meterline

Meterline is a usage metering and billing engine. Its core invariant is:

> Every usage event that enters the system appears in exactly one invoice line, at the correct price, exactly once.

Day 1 builds the correctness foundation: immutable raw usage ingestion with deterministic event IDs and database-enforced deduplication.

Day 2 adds a pure pricing engine for volume pricing, marginal tiered pricing, exact effective-dated rate boundaries, integer-cent totals, and property-based pricing tests.

Day 3 adds billing periods, deterministic usage aggregation, invoice generation, rerunnable billing jobs, and explicit late-event adjustment lines.

Day 4 adds independent reconciliation from immutable raw events, Kafka ingestion/replay components, Docker packaging, local `kind` manifests, and benchmark/recovery runbooks.

## Day 1: Ingestion Foundation

Implemented so far:

- Java 21 Spring Boot backend.
- PostgreSQL-backed raw usage event store.
- Flyway migration for `raw_usage_events`.
- REST ingestion endpoint at `POST /api/events`.
- Deterministic event IDs derived from stable business action fields.
- PostgreSQL `PRIMARY KEY` and unique business-action index for deduplication.
- Append-only event model.
- Explicit adjustment records instead of historical edits.
- Synthetic seven-day event generator with known totals.
- Testcontainers tests for real PostgreSQL behavior.

## Event Schema

Raw usage events include:

- `event_id`
- `customer_id`
- `meter_id`
- `source`
- `source_event_key`
- `quantity_units`
- `event_timestamp`
- `received_at`
- `event_type`
- `adjustment_for_event_id`
- `metadata`

`event_id` is deterministic and is generated from:

- customer
- meter
- source
- source event key
- event timestamp
- event type
- adjustment target, when present

The database enforces uniqueness. Duplicate delivery is safe because inserting the same event again uses `ON CONFLICT DO NOTHING`.

## Running Locally

Start PostgreSQL with a database named `meterline`, user `meterline`, and password `meterline`, then run:

```bash
mvn spring-boot:run
```

## Ingesting An Event

```bash
curl -i -X POST http://localhost:8080/api/events \
  -H 'Content-Type: application/json' \
  -d '{
    "customerId": "cust-1",
    "meterId": "api-calls",
    "source": "demo",
    "sourceEventKey": "request-123",
    "quantityUnits": 42,
    "eventTimestamp": "2026-01-01T00:00:00Z",
    "eventType": "USAGE",
    "metadata": {
      "path": "/v1/messages"
    }
  }'
```

The first request returns `201 Created` with status `inserted`. Repeating the same request returns `200 OK` with status `duplicate_ignored`.

## Day 1 Correctness Proof

The Day 1 tests cover:

- valid event ingestion
- malformed event rejection
- deterministic ID generation
- duplicate submission
- concurrent duplicate submission
- seven-day backfill replay safety
- adjustment records as new rows
- raw event immutability by absence of update behavior

Run tests with:

```bash
mvn test
```

The PostgreSQL integration tests use Testcontainers and require Docker. If Docker is unavailable, those tests are skipped rather than silently replaced with a mock database.

## Day 2 Pricing Engine

Implemented so far:

- Pure pricing module with no Spring, database, clock, HTTP, or randomness dependency.
- Volume pricing.
- Marginal tiered pricing.
- Effective-dated rate changes split at exact timestamps.
- Integer-cent `Money` outputs.
- Rates represented as millionths of a cent per unit.
- Explicit `HALF_UP` line-level rounding policy.
- jqwik property tests for monotonicity, tier continuity, period split additivity, and determinism.
- Precision test for 10 million events at `$0.0001/unit`.

See [docs/day-2-pricing.md](docs/day-2-pricing.md) for pricing assumptions and rounding notes.

## Day 3 Billing

Implemented so far:

- PostgreSQL billing tables for periods, aggregates, invoices, and invoice lines.
- Deterministic IDs for billing periods, aggregates, invoices, usage lines, and adjustment lines.
- Idempotent period billing that can be rerun after a partial failure without duplicating invoice lines.
- Usage aggregation from immutable raw events by customer, meter, and billing period.
- Invoice totals recomputed from invoice lines instead of incrementally mutated.
- Explicit adjustment lines for events that arrive after their original period is closed.
- Command-line billing runner with `billing:run <period-start> <period-end>`.
- Integration tests for stable invoices, partial-state recovery, and late-event adjustments.

See [docs/day-3-billing.md](docs/day-3-billing.md) for billing lifecycle, late-arrival, and crash-recovery notes.

## Day 4 Reconciliation, Kafka, And Kubernetes

Implemented so far:

- Independent reconciliation module that recomputes expected invoice amounts from `raw_usage_events`.
- Machine-readable and human-readable reconciliation reports.
- One-cent corruption detection for issued invoices.
- Command-line reconciliation runner with `reconcile:run <period-start> <period-end> [--json]`.
- Kafka producer, consumer, topic configuration, and replay service using the same idempotent raw event writer.
- Dockerfile, Docker Compose runtime, and local `kind` manifests.
- Benchmark and pod-recovery runbooks with placeholders for measured results.

See [docs/day-4-reconciliation-kafka-k8s.md](docs/day-4-reconciliation-kafka-k8s.md) and [docs/day-4-benchmark.md](docs/day-4-benchmark.md).
