# Meterline Master Plan

Use this file as persistent Codex context for the Meterline project. Meterline is a usage metering and billing engine whose defining feature is not the billing pipeline itself, but the proof that the pipeline billed correctly.

## Objective

Build a backend/infrastructure portfolio project that ingests usage events, deduplicates them, aggregates them, prices them, generates invoices, independently reconciles issued invoices against immutable raw events, and lets an operator drill from any invoice line back to the exact raw events that produced it.

The project invariant is:

> Every usage event that enters the system appears in exactly one invoice line, at the correct price, exactly once. The sum of the raw events for a customer over a period equals the invoice total, to the cent.

Everything in the architecture exists to defend this invariant.

## Core Problem

Usage-based billing systems silently lose or overstate revenue when events are dropped, duplicated, priced under stale rates, processed after crashes, or mishandled after late arrival. The customer often cannot detect underbilling, and ordinary invoices can look plausible while still being wrong.

Meterline solves this by treating reconciliation and traceability as first-class product behavior:

- Usage events are immutable source-of-truth records.
- Deduplication is enforced by PostgreSQL uniqueness, not application-only checks.
- Pricing is deterministic and money-safe.
- Invoices are generated idempotently.
- A separate reconciliation job independently recomputes expected totals from raw events.
- Any mismatch fails loudly with customer, period, expected amount, actual amount, and difference.
- Any invoice line can be traced back to its raw source events.

## Scope Guard

Keep the project focused. Do not expand into product areas that distract from correctness.

In scope:

- One usage event schema.
- One currency.
- Two pricing models: volume pricing and marginal tiered pricing.
- Effective-dated rates with two rate versions comparable at a time.
- Deterministic event IDs derived from business actions.
- Append-only raw event storage.
- Corrections as adjustment records, never edits to historical raw events.
- REST ingestion API.
- Kafka ingestion path for volume, replay, and at-least-once delivery.
- PostgreSQL as the system of record.
- Flyway migrations.
- Testcontainers integration tests.
- jqwik property-based tests for pricing.
- Docker and local Kubernetes using `kind`.
- Grafana and CLI only for visibility and operation.
- README, technical write-up, benchmark results, reconciliation report, and interview/resume artifacts.

Out of scope:

- Taxes.
- Dunning.
- Payment provider integration.
- Multi-currency support.
- Multi-tenancy beyond `customer_id`.
- React dashboard.
- Microservices.
- Redis or caching before measured need.
- Stripe integration before reconciliation works.

If time runs short, extend the schedule. Do not cut reconciliation, drill-down, idempotency, failure-scenario tests, or money-safety behavior.

## Required Technology Choices

- Java 21 for backend implementation, strong typing, records, sealed interfaces, and money-safe domain modeling.
- Spring Boot for the application and REST API.
- PostgreSQL as the source of truth because the invariant depends on transactions, joins, and database-enforced uniqueness.
- Flyway for schema migrations.
- Integer cents for final money amounts everywhere. Never use floating point for money.
- Scaled integer rates and `BigDecimal` only where fractional division requires it, with an explicit documented rounding mode.
- Kafka for high-volume ingestion, at-least-once delivery, replay, and out-of-order event behavior.
- Idempotent writers and consumers instead of relying on exactly-once Kafka configuration as the correctness proof.
- Testcontainers for real PostgreSQL and Kafka integration tests.
- jqwik for property-based testing of pricing laws.
- Docker for repeatable local runtime.
- `kind` for local Kubernetes.
- Grafana for monitoring; no custom React UI.

## Architecture

Use one service with clear module boundaries.

Recommended modules:

- `ingestion`: REST and Kafka ingestion, validation, deterministic event ID handling.
- `events`: immutable raw event store, adjustment records, partition-aware schema.
- `pricing`: pure pricing engine with pricing models, rate versions, integer cents, and rounding policy.
- `aggregation`: period aggregation by customer, meter, and period.
- `invoicing`: invoice generation, invoice lines, totals, lifecycle, adjustment lines.
- `reconciliation`: independent recomputation from raw events and mismatch reporting.
- `drilldown`: invoice-line-to-raw-events traceability queries.
- `operations`: CLI jobs, synthetic generator, benchmarks, reports.
- `observability`: metrics exposed for Grafana.

Data flow:

```text
Usage Events
  -> REST API or Kafka topic
  -> validation
  -> deterministic event ID
  -> PostgreSQL UNIQUE constraint deduplication
  -> immutable raw event store
  -> aggregation
  -> pure pricing engine
  -> invoice lines and totals in integer cents
  -> independent reconciliation from raw events
  -> invoice-line drill-down to source events
```

## Non-Negotiable Invariants

- Raw usage events are append-only and immutable.
- Corrections are explicit adjustment records.
- Duplicate event delivery must be harmless.
- The database must enforce event uniqueness.
- Every billable event is billed exactly once.
- Every invoice line is traceable to source events or adjustment records.
- Pricing is pure: no I/O, no current time, no randomness.
- Billing and aggregation jobs are idempotent.
- Re-running ingestion for a historical window must not change totals unless new legitimate adjustment records are introduced.
- Late events are never silently dropped.
- Closed-period late events become visible adjustment lines in the next period.
- All final money totals are integer cents.
- Reconciliation recomputes from raw events independently of previously aggregated totals.
- A deliberately corrupted invoice must be detected.

## Failure Scenarios To Preserve

| # | Scenario | Pass Criterion | Primary Day |
|---|---|---|---|
| 1 | Duplicate delivery: same event ingested three times | Billed once; database `UNIQUE` constraint enforces dedup | Day 1 |
| 2 | Crash mid-aggregation | Rerun produces byte-identical invoices; no partial state or double billing | Day 3 |
| 3 | Late arrival: Jan 31 event arrives Feb 2 | Goes into January if open; otherwise explicit February adjustment line | Day 3 |
| 4 | Mid-period price change | Usage before change bills at old rate; boundary exact to the second | Day 2 |
| 5 | Tier boundary crossing | Marginal pricing correct at the exact crossing unit | Day 2 |
| 6 | Reconciliation mismatch | Independent recomputation matches to the cent; planted corruption is caught | Day 4 |
| 7 | Float drift: 10M events at $0.0001 | Zero cent-level error across the full period | Day 2 |
| 8 | Backfill safety: re-run ingestion for last 7 days | Totals do not change | Day 1 |

## Five-Day Sequence

### Day 1: Backend Foundation, Database, Event Ingestion

Build the Java 21 Spring Boot foundation, PostgreSQL, Flyway migrations, raw event schema, REST ingestion API, deterministic event IDs, append-only event storage, adjustment records, synthetic event generator, and Testcontainers integration tests.

Exit with duplicate delivery and seven-day backfill safety passing.

### Day 2: Pricing Engine And Financial Accuracy

Build a pure pricing engine for volume and marginal tiered pricing, effective-dated rates, exact rate boundaries, integer cents, scaled rates, explicit rounding, and jqwik property-based tests.

Exit with tier boundaries, mid-period rate changes, and 10M-event precision passing.

### Day 3: Aggregation, Invoicing, Late Events, Crash Recovery

Build idempotent aggregation, invoice generation, period closing, late-arrival behavior, visible adjustment lines, CLI job execution, transaction boundaries, and crash/fault-injection tests.

Exit with byte-identical invoices after crash and rerun, plus explicit late-event behavior.

### Day 4: Reconciliation, Kafka, Kubernetes, Scale

Build independent reconciliation, mismatch reporting, Kafka producer and idempotent consumer, replay behavior, out-of-order delivery tests, Docker packaging, local `kind` deployment, pod-kill recovery, and 10M-event benchmark.

Exit with planted invoice corruption detected and measured ingestion/close-run performance.

### Day 5: Drill-Down, Observability, Documentation, Portfolio

Build invoice-line-to-events drill-down, Grafana metrics, final end-to-end tests, README, architecture write-up, reconciliation report, benchmark summary, resume bullets, interview answers, and outreach message.

Exit with a demonstrable portfolio project where a user can start from an invoice total and walk back to source events.

## Resume Metrics To Produce From Tests

Do not invent these values. Fill them only after test or benchmark output exists.

1. Built a usage metering and billing engine ingesting `[X]M` events at `[Y]/s`, producing invoices reconciled to the cent against an immutable raw event log.
2. Guaranteed exactly-once billing via database-enforced idempotency and crash-safe idempotent aggregation, verified across `[N]` injected failure scenarios including mid-run crashes, duplicate delivery, and late-arriving events.
3. Implemented tiered and time-sliced pricing in integer cents with property-based tests, eliminating float drift across `[X]M` events at `$0.0001/unit`.
4. Built an automated reconciliation job that independently recomputes invoices from raw events, detecting `[N]/[N]` injected billing corruptions.

## Final Demonstration Script

The final project should demonstrate:

1. Generate synthetic usage with known correct totals.
2. Ingest the same batch multiple times and show deduped counts.
3. Run billing and generate invoices.
4. Show exact pricing around tier and rate-change boundaries.
5. Inject a crash and rerun the billing job.
6. Inject a late event and show either original-period inclusion or visible adjustment.
7. Corrupt an invoice by one cent and run reconciliation.
8. Show the reconciliation failure report.
9. Open an invoice line and list the exact raw events behind it.
10. Show benchmark and Grafana results.

## Interview Positioning

Thirty-second explanation:

> Companies that bill by usage count billions of events, but the dangerous part is knowing whether the invoice is actually right. I built Meterline, a metering and billing engine that deduplicates usage, prices it, generates invoices, then independently recomputes every invoice from the immutable raw event log. If the numbers disagree, it fails loudly and shows the customer, period, and difference. You can also drill from any invoice line back to the exact raw events behind it.

Lead with reconciliation and drill-down. The pipeline is ordinary; the proof is the project.
