# Day 1 Prompt: Meterline Backend Foundation, Database, And Event Ingestion

You are Codex working in VS Code on the Meterline project. Implement Day 1 without dropping any scope from the master plan.

## Objective

Build a working Java 21 Spring Boot backend that accepts usage events, validates them, stores them safely in PostgreSQL, prevents duplicates with a database constraint, preserves raw events immutably, supports explicit correction records, and includes a synthetic event generator with known correct totals.

Day 1 must prove failure scenarios 1 and 8:

- Duplicate delivery: the same event ingested three times is stored and billed once.
- Backfill safety: re-running ingestion for the last seven days does not change totals.

## Context

Meterline is a usage metering and billing engine whose core invariant is:

> Every usage event that enters the system appears in exactly one invoice line, at the correct price, exactly once. The sum of the raw events for a customer over a period equals the invoice total, to the cent.

Reconciliation and drill-down are the most important project features, but they depend on a trustworthy raw event store. Day 1 creates that trust foundation.

Use the project master plan as persistent context. Keep `sources/` reference files read-only.

## Scope

Build these foundations:

- Java 21 Spring Boot project.
- PostgreSQL configuration.
- Flyway database migrations.
- Usage event schema.
- REST ingestion API.
- Input validation.
- Deterministic event IDs derived from business actions.
- Database-enforced deduplication with a PostgreSQL `UNIQUE` constraint.
- Append-only immutable raw event storage.
- Time-aware event structure suitable for later partitioning by event time.
- Explicit correction or adjustment records rather than edits.
- Synthetic event generator that produces usage with known correct totals.
- Testcontainers integration tests using real PostgreSQL.

Do not implement pricing, invoicing, Kafka, Kubernetes, Grafana, or payment integration today.

## Implementation Tasks

1. Initialize or update the project as a Java 21 Spring Boot application.
2. Add dependencies for Spring Web, validation, PostgreSQL, Flyway, Testcontainers, and the test framework already chosen by the project.
3. Configure local development properties for PostgreSQL.
4. Create Flyway migrations for the Day 1 schema.
5. Model raw usage events with at least:
   - `event_id`
   - `customer_id`
   - `meter_id`
   - business action fields used to derive the deterministic ID
   - quantity or units
   - event timestamp
   - received timestamp
   - source or producer identifier
   - event type, including normal usage and adjustment/correction records
   - metadata suitable for later drill-down
6. Enforce `event_id` uniqueness in PostgreSQL.
7. Make raw events append-only:
   - Do not build update behavior for historical usage rows.
   - Do not silently overwrite existing events.
   - Represent corrections as new adjustment records.
8. Implement a deterministic event ID strategy based on stable business fields rather than database auto-increment.
9. Implement a REST endpoint for ingesting usage events.
10. Validate required fields and reject malformed input with useful errors.
11. Make duplicate ingestion idempotent:
    - A repeated valid event should not create a second row.
    - Concurrent duplicate submissions should still be safe because PostgreSQL enforces uniqueness.
12. Create a synthetic event generator that can produce:
    - deterministic customers
    - deterministic meters
    - deterministic timestamps
    - known total units per customer/meter/period
    - duplicate batches for replay testing
13. Add a command or test helper for replaying the same generated batch multiple times.
14. Add a basic query or repository method that computes stored event counts and units by customer, meter, and date range.
15. Document the Day 1 data model decisions in the README or a dedicated docs file.

## Required Tests

Use Testcontainers with real PostgreSQL for integration tests. Do not mock the repository for behavior that depends on database constraints.

Required tests:

- Submit a valid event and verify it is stored.
- Submit malformed data and verify the request is rejected.
- Submit the same event three times and verify only one raw event exists.
- Submit duplicate events concurrently and verify PostgreSQL prevents duplication.
- Replay a seven-day generated batch and verify totals do not change after the first successful ingestion.
- Attempt to modify historical usage through the application surface and verify raw events remain immutable or that no such update path exists.
- Insert an adjustment/correction record and verify it is stored as a new record linked to the original context rather than replacing the original event.
- Verify deterministic event ID generation returns the same ID for the same business action and different IDs for different business actions.

## Constraints

- Do not edit, rename, move, or delete files under `sources/`.
- Do not use floating point for billable quantities or money-related values.
- Deduplication must be enforced by PostgreSQL, not only by an application pre-check.
- Raw usage events must be append-only.
- Corrections must be explicit records.
- Do not add Kafka yet; Kafka enters on Day 4.
- Do not add a React dashboard.
- Do not add Stripe or any payment provider.
- Keep the service monolithic with clear module boundaries.
- Prefer readable, boring code over clever abstractions.

## Exit Criteria

Day 1 is complete only when:

- The application starts locally.
- Flyway creates the schema successfully.
- The ingestion API accepts valid usage events.
- Malformed events are rejected.
- Duplicate events are harmless.
- Concurrent duplicate ingestion is safe because of a database uniqueness constraint.
- The same seven-day generated batch can be replayed without changing totals.
- Raw events are immutable from the application’s perspective.
- Corrections are represented as adjustment records.
- Day 1 tests pass.

## Expected Deliverables

- Working Spring Boot backend foundation.
- Flyway migrations for raw events and adjustments.
- REST ingestion API.
- Deterministic event ID implementation.
- Synthetic event generator with known totals.
- Testcontainers integration test suite.
- Short Day 1 documentation explaining:
  - event schema
  - deterministic ID strategy
  - deduplication guarantee
  - append-only storage
  - replay/backfill result
