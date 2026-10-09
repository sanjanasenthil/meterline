# Meterline: Project Overview

## Summary

Meterline is a Java 21 usage metering and billing backend. It accepts usage events over REST or Kafka, prevents duplicate billing, aggregates and prices usage, creates invoices, independently reconciles issued invoices against immutable source events, and traces invoice lines back to those events.

The core correctness rule is that billable usage is represented once at the configured price and remains explainable from source records. PostgreSQL uniqueness provides ingestion idempotency, stable billing identifiers make reruns safe, reconciliation independently checks invoice accuracy, and drill-down exposes the evidence behind each line.

## Implemented Scope

### Day 1: Ingestion And Data Integrity

- Java 21 and Spring Boot REST application with `POST /api/events`.
- PostgreSQL raw event storage and Flyway schema migrations.
- Deterministic event IDs backed by database uniqueness constraints.
- Append-only raw events; corrections use adjustment events rather than edits.
- Request validation and a synthetic seven-day batch generator.
- PostgreSQL integration tests for valid and invalid events, duplicate and concurrent-duplicate ingestion, backfill replay, and adjustments.

### Day 2: Pricing

- Framework-independent pricing package with volume and marginal tiered models.
- Effective-dated rates split at exact timestamps.
- Integer cents for final money amounts, scaled rates in millionths of a cent, and `BigDecimal` arithmetic with explicit `HALF_UP` rounding.
- Example and jqwik property coverage for determinism, monotonicity, tier transitions, time splits, and fractional-rate arithmetic.
- A 10-million-unit arithmetic test at `$0.0001` per unit; this is not a 10-million-row database load test.

### Day 3: Aggregation And Invoicing

- PostgreSQL billing periods, usage aggregates, invoices, and invoice lines.
- Stable IDs and upserts support repeatable billing without duplicating invoice lines or incrementing totals twice.
- Invoice totals are recalculated from invoice lines.
- Late events received before period closure are included normally. Events received after closure produce explicit adjustment lines in the target period.
- Command-line jobs: `billing:run` and `billing:issue`.
- Integration coverage for stable invoices, partial invoice-line recovery, and late-arrival adjustments.

### Day 4: Reconciliation, Kafka, And Runtime Packaging

- Reconciliation recalculates expected amounts from `raw_usage_events`, independently of stored aggregates.
- Reports include expected and actual totals, differences, and mismatch details; `reconcile:run` supports JSON output.
- Integration coverage plants a one-cent invoice-line corruption and verifies that reconciliation detects and quantifies it.
- Kafka producer, consumer, topic configuration, and replay path use the same idempotent PostgreSQL writer. Delivery is at-least-once; correctness does not depend on Kafka exactly-once mode.
- Dockerfile, Docker Compose services, and local `kind` manifests are included.

### Day 5: Invoice Drill-Down And Observability

- `GET /api/invoices/{invoiceId}` returns invoice identity, customer, period, status, total, creation time, and lines.
- `GET /api/invoice-lines/{lineId}/drilldown` returns line data and its source events, including timestamps, quantities, source, event type, adjustment references, and metadata.
- Drill-down responses include summed source quantity, applied-rate amount, and quantity/amount consistency checks.
- Adjustment lines are marked and trace to their referenced source event, including late-arrival adjustments.
- Micrometer metrics cover ingestion inserts, duplicates and failures; billing duration, errors and processed invoices; and reconciliation runs, mismatches, duration and errors.
- Prometheus scrapes `/actuator/prometheus`; Docker Compose provisions Grafana and the **Meterline Operations** dashboard.
- Portfolio material includes architecture notes, reconciliation and drill-down examples, resume bullets, an interview guide, and an outreach message.

## Architecture

```text
REST API or Kafka
       |
       v
validate -> deterministic event ID -> PostgreSQL uniqueness check
                                      |
                                      v
                             immutable raw events
                               /           \
                              v             v
                     aggregate + price   reconcile from raw events
                              |             |
                              v             v
                         invoice lines <- compare issued totals
                              |
                              v
                    line -> source-event drill-down

Actuator metrics -> Prometheus -> Grafana
```

Meterline is one Spring Boot service organized into event, pricing, billing, reconciliation, Kafka, and drill-down packages. PostgreSQL is the source of truth. Database uniqueness protects ingestion, stable IDs protect billing reruns, and independent reconciliation checks issued invoices.

## Technology Choices

| Technology | Purpose |
|---|---|
| Java 21, Spring Boot | Typed backend domain, REST API, jobs, and Actuator. |
| PostgreSQL | Transactional source of truth, constraints, aggregation, and traceability joins. |
| Flyway | Versioned schema migrations. |
| Integer cents, scaled integer rates, `BigDecimal` | Exact monetary representation and controlled fractional-rate rounding. |
| Kafka | At-least-once event publication, consumption, and replay. |
| Testcontainers | Integration tests with real PostgreSQL and Kafka services; requires Docker. |
| jqwik | Property-based pricing checks. |
| Docker Compose, `kind` | Local multi-service runtime and Kubernetes manifests. |
| Micrometer, Prometheus, Grafana | Application metrics, scraping, and operations dashboard. |

## Run Locally

Requirements: Java 21, Maven, and Docker Compose. Set local passwords in the shell; do not commit them.

```bash
export METERLINE_POSTGRES_PASSWORD='<choose-a-local-password>'
export GRAFANA_ADMIN_PASSWORD='<choose-a-local-password>'
docker compose up --build
```

The app is available at `http://localhost:8080`, Prometheus at `http://localhost:9090`, and Grafana at `http://localhost:3000`. Metrics are exposed at `http://localhost:8080/actuator/prometheus`.

Submit a usage event:

```bash
curl -i -X POST http://localhost:8080/api/events \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-1","meterId":"api-calls","source":"demo","sourceEventKey":"request-123","quantityUnits":42,"eventTimestamp":"2026-01-01T00:00:00Z","eventType":"USAGE","metadata":{"path":"/v1/messages"}}'
```

Run billing, issue invoices, inspect an invoice and line, then reconcile the period. Replace the sample period and IDs with the relevant values:

```bash
mvn spring-boot:run --args="billing:run 2026-01-01T00:00:00Z 2026-02-01T00:00:00Z"
mvn spring-boot:run --args="billing:issue 2026-01-01T00:00:00Z 2026-02-01T00:00:00Z"
curl http://localhost:8080/api/invoices/<invoice-id>
curl http://localhost:8080/api/invoice-lines/<line-id>/drilldown
mvn spring-boot:run --args="reconcile:run 2026-01-01T00:00:00Z 2026-02-01T00:00:00Z --json"
```

Run tests with:

```bash
mvn test
```

PostgreSQL and Kafka integration tests use Testcontainers and require a running Docker engine. In the measured run, Maven 3.9.9 and Java 21 ran in a container connected to Docker Desktop because Maven was not available on the host command path.

## Verification And Benchmark Evidence

The full Docker-backed Maven run completed with:

```text
Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

This run exercised PostgreSQL and Kafka Testcontainers. Testcontainers is pinned to 1.21.4 for compatibility with Docker Engine 29.8.2.

The Kafka duplicate-replay test ran three times. Each run generated 100,000 unique events across 20 customers, replayed the entire set once, and sent 1,000 additional duplicates concurrently from four producer threads. Each run delivered 201,000 messages, stored exactly 100,000 raw events, rejected 101,000 duplicates, billed zero duplicate units, produced an independently computed 550,000-cent invoice total, and reported zero reconciliation mismatches.

| Run | Ingestion incl. consumer drain | Billing | Reconciliation |
|---:|---:|---:|---:|
| 1 | 74,700 ms | 166 ms | 37 ms |
| 2 | 72,051 ms | 162 ms | 33 ms |
| 3 | 74,635 ms | 170 ms | 28 ms |

Measured machine: MacBook Pro with Apple M1 Pro, 8 CPU cores, and 16 GB RAM. Docker Desktop allocation: 8 CPUs and 7.748 GiB. These are local observations, not a production capacity claim.

## Known Limits And Unverified Work

- The replay benchmark is 100,000 unique stored events, not a 10-million-row load test. The 10-million figure elsewhere is an aggregate pricing arithmetic case.
- Docker Compose Prometheus/Grafana connectivity and `kind` pod deletion/recovery were not measured.
- Partial-state recovery removes missing invoice-line state and reruns billing; it does not kill a process at an arbitrary instruction inside a transaction.
- The active billing catalog selects a default volume plan. Customer-specific effective-dated plan selection is not wired end to end.
- Invoice lines persist the applied rate but not a plan ID or explicit rate-version ID. Drill-down labels the period start as the rate-version timestamp.
- The project supports one event schema and one currency. It does not include taxes, dunning, payment-provider integration, a customer-facing React UI, Redis, or microservices.
- Kafka consumer lag is not instrumented.

## Related Documents

- [README](../README.md)
- [Architecture and technology decisions](architecture-and-decisions.md)
- [Final test summary](final-test-summary.md)
- [Kafka replay benchmark results](day-4-benchmark.md)
- [Reconciliation example](reconciliation-report-example.md)
- [Invoice drill-down example](drilldown-example.md)
- [Resume bullets](resume-bullets.md)
- [Interview guide](interview-guide.md)
- [Outreach message](outreach-message.md)
