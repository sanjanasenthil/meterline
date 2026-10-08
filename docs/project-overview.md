# Meterline Project Overview

## What Meterline Does

Meterline is a usage metering and billing backend. It accepts usage events, deduplicates them, aggregates and prices usage, creates invoices, independently reconciles issued invoices against the original event log, and lets an operator trace an invoice line back to its source events.

The project is built around one correctness rule: billable usage should be represented exactly once at the intended price, and the invoice should be explainable from immutable source records. Reconciliation is the independent check; drill-down is the evidence trail.

## Work Completed Across The Five Days

### Day 1: Event Ingestion

- Java 21 and Spring Boot application with a REST ingestion API at `POST /api/events`.
- PostgreSQL raw event storage, managed through Flyway migrations.
- Deterministic event IDs and database-enforced uniqueness make repeat delivery harmless.
- Raw event records are append-only. Corrections are represented as adjustment records.
- Request validation and a synthetic seven-day batch generator support repeatable tests.
- PostgreSQL integration tests cover valid/invalid ingestion, duplicate submissions, concurrent duplicates, backfill replay, and adjustments. These tests use Testcontainers and require Docker.

### Day 2: Pricing

- Pure pricing package with no Spring or database dependency.
- Volume and marginal tiered pricing models.
- Effective-dated rates split at exact timestamps.
- Final amounts use integer cents. Rates use millionths of a cent per unit. Fractional calculations use `BigDecimal` with explicit `HALF_UP` rounding.
- Example and jqwik property tests cover determinism, monotonicity, tier transitions, time splits, and an aggregate of 10 million units at `$0.0001` with zero cent-level drift.

### Day 3: Aggregation And Invoicing

- Billing periods, usage aggregates, invoices, and invoice lines are stored in PostgreSQL.
- Stable IDs and upserts allow a billing period to be rerun without duplicating invoice lines or adding totals twice.
- Invoice totals are recomputed from their lines.
- Late usage for an open period is included there. Usage received after its original period is closed becomes an explicit adjustment line in the target period.
- Billing commands include `billing:run` and `billing:issue`.
- Integration coverage checks stable invoices, recovery after partial invoice-line state, and late-arrival adjustments.

### Day 4: Independent Reconciliation And Runtime

- Reconciliation calculates expected billing from `raw_usage_events`; it does not trust stored aggregates.
- Reports include expected and actual totals, differences, and mismatch details. The CLI command is `reconcile:run` and supports JSON output.
- A test helper plants a one-cent invoice-line corruption so reconciliation can prove it detects the mismatch.
- Kafka producer, consumer, and replay service use the same idempotent PostgreSQL writer. The delivery model is at-least-once.
- Docker packaging, Docker Compose, and local `kind` manifests describe the app, PostgreSQL, and Kafka runtime.
- A Kafka duplicate-replay benchmark is measured at 100,000 unique events; remaining unmeasured runtime checks are called out explicitly in the evidence documents.

### Day 5: Drill-Down, Observability, And Portfolio Material

- `GET /api/invoices/{invoiceId}` returns invoice identity, customer, period, status, total cents, creation time, and lines.
- `GET /api/invoice-lines/{lineId}/drilldown` returns line details and source events with timestamps, quantities, source, event type, adjustment link, and metadata.
- The drill-down response includes summed returned quantity and checks whether it matches the invoice line, plus an applied-rate amount calculation and amount check.
- Adjustment lines are marked separately and trace to their exact referenced source event, including the late-arrival case.
- Micrometer metrics cover inserted and duplicate events, ingestion failures, billing duration and errors, invoice rows processed, reconciliation runs, mismatches, duration, and errors.
- Prometheus scrapes `/actuator/prometheus`. Docker Compose provisions Grafana with the **Meterline Operations** dashboard.
- A command-line invoice issue runner and Day 5 integration test coverage were added.
- The README now leads with the billing correctness problem, reconciliation, and event-level drill-down. Additional documents cover architecture and decisions, test status, benchmark limitations, report examples, drill-down examples, resume bullets, interview questions, and an outreach message.

## Architecture At A Glance

```text
REST API or Kafka
       |
       v
validate request -> deterministic event ID -> PostgreSQL uniqueness check
                                             |
                                             v
                                  immutable raw usage events
                                             |
                        +--------------------+--------------------+
                        |                                         |
                        v                                         v
              aggregate and price                         reconcile independently
                        |                              from immutable raw events
                        v                                         |
                 invoice and lines <------------------------------+
                        |
                        v
          invoice -> line -> source event drill-down

Actuator metrics -> Prometheus -> Grafana
```

The implementation is one Spring Boot service organized into event, pricing, billing, reconciliation, Kafka, and drill-down packages. PostgreSQL is the source of truth. Database uniqueness and stable billing IDs provide idempotency; Kafka exactly-once configuration is not used as the correctness guarantee.

## Main Technologies And Why

| Technology | Role in Meterline |
|---|---|
| Java 21 and Spring Boot | Typed backend domain, REST API, scheduled/command-line jobs, and Actuator. |
| PostgreSQL | Transactional source of truth, unique constraints, aggregation, and traceability joins. |
| Flyway | Versioned database migrations. |
| Integer cents and scaled integer rates | Exact final money representation without floating-point money math. |
| `BigDecimal` | Fractional-rate arithmetic with explicit rounding. |
| Kafka | At-least-once ingestion and replay path. |
| Testcontainers | Integration tests against real PostgreSQL and Kafka services. Requires Docker. |
| jqwik | Property-based pricing checks in addition to example tests. |
| Docker Compose and `kind` | Repeatable local services and local Kubernetes manifests. |
| Micrometer, Prometheus, Grafana | Application metrics, scrape endpoint, and provisioned operations dashboard. |

## Run And Inspect

Requirements: Java 21, Maven, and Docker Compose. Set local passwords in environment variables; the repository does not need committed passwords.

```bash
export METERLINE_POSTGRES_PASSWORD='<choose-a-local-password>'
export GRAFANA_ADMIN_PASSWORD='<choose-a-local-password>'
docker compose up --build
```

The app is at `http://localhost:8080`, Prometheus at `http://localhost:9090`, Grafana at `http://localhost:3000`, and the Prometheus metrics endpoint is `http://localhost:8080/actuator/prometheus`.

In another shell, submit an event:

```bash
curl -i -X POST http://localhost:8080/api/events \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-1","meterId":"api-calls","source":"demo","sourceEventKey":"request-123","quantityUnits":42,"eventTimestamp":"2026-01-01T00:00:00Z","eventType":"USAGE","metadata":{"path":"/v1/messages"}}'
```

Run billing, issue the invoices, and inspect an invoice and its line. Replace the sample period and IDs with values from the database/API response:

```bash
mvn spring-boot:run --args="billing:run 2026-01-01T00:00:00Z 2026-02-01T00:00:00Z"
mvn spring-boot:run --args="billing:issue 2026-01-01T00:00:00Z 2026-02-01T00:00:00Z"
curl http://localhost:8080/api/invoices/<invoice-id>
curl http://localhost:8080/api/invoice-lines/<line-id>/drilldown
mvn spring-boot:run --args="reconcile:run 2026-01-01T00:00:00Z 2026-02-01T00:00:00Z --json"
```

Run automated tests with:

```bash
mvn test
```

## Verification And Evidence

The final Docker-backed `mvn test` run completed with **34 tests, 0 failures, 0 errors, and 0 skipped**. PostgreSQL and Kafka Testcontainers both ran against Docker Desktop. The Testcontainers dependency is pinned to 1.21.4 for compatibility with the installed Docker Engine.

The Kafka duplicate-replay scale test passed three times at 100,000 unique events per run, with 201,000 total deliveries, 101,000 rejected duplicates, zero duplicate units billed, an independently calculated 550,000-cent invoice total, and zero reconciliation mismatches. End-to-end ingestion including consumer drain took 74,700 ms, 72,051 ms, and 74,635 ms; billing took 166 ms, 162 ms, and 170 ms; reconciliation took 37 ms, 33 ms, and 28 ms. Machine: Apple M1 Pro MacBook Pro, 8 CPU cores, 16 GB RAM; Docker Desktop allocation: 8 CPUs and 7.748 GiB. Full run details are in [docs/final-test-summary.md](final-test-summary.md) and [docs/day-4-benchmark.md](day-4-benchmark.md).

The 10-million figure refers to a single aggregate pricing calculation, not ten million stored events. Docker Compose Prometheus/Grafana connectivity and `kind` pod deletion/recovery were not measured. The crash-recovery test repairs partial invoice-line state and reruns billing; it does not kill a process at an arbitrary instruction inside a transaction.

## Known Limits

- One event schema and one currency.
- The pricing engine supports volume and marginal tiered pricing; the active billing catalog currently selects a default volume plan.
- Pricing tests cover mid-period effective rate changes, but selecting a customer-specific changing plan through the invoice workflow is not yet wired end to end.
- The invoice schema persists applied rate but not a plan ID or explicit rate-version ID. Drill-down reports the rate and uses the invoice period start as the effective timestamp label.
- No taxes, dunning, payment provider integration, multi-currency, React UI, Redis, or microservices.
- Kafka consumer lag is not instrumented.
- Docker Compose observability and `kind` recovery still need their own runtime verification; PostgreSQL/Kafka integration coverage passed with Docker Desktop in the recorded run.

## Related Documents

- [README](../README.md)
- [Architecture and technology decisions](architecture-and-decisions.md)
- [Final test summary](final-test-summary.md)
- [Day 4 benchmark notes](day-4-benchmark.md)
- [Reconciliation example](reconciliation-report-example.md)
- [Drill-down example](drilldown-example.md)
- [Resume bullets](resume-bullets.md)
- [Interview guide](interview-guide.md)
- [Outreach message](outreach-message.md)

## GitHub

Day 5 was pushed to `main` in commit [`bc7448b`](https://github.com/sanjanasenthil/meterline/commit/bc7448bd0d791c852f7994ce8177aeb5386868bc). This overview document was created afterward and is currently a local workspace file.
