# Meterline

Meterline is a usage metering and billing engine. Its core invariant is:

> Every usage event that enters the system appears in exactly one invoice line, at the correct price, exactly once.

Day 1 builds the correctness foundation: immutable raw usage ingestion with deterministic event IDs and database-enforced deduplication.

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
