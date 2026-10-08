# Final Test Summary

Verified on 2026-10-08 with Docker Desktop running and Java 21.0.7. The suite ran from a Maven 3.9.9 / Eclipse Temurin 21 container because Maven is not installed on the host command path. The container used Docker Desktop's mounted socket; Testcontainers connected to Engine 29.8.2.

```text
Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The previous Docker-gated skips are no longer present in this run. Updating Testcontainers to 1.21.4 allowed it to negotiate with the installed Docker Engine API; no integration tests or assertions were removed or weakened.

## Verified Scenarios

| Scenario | Result |
|---|---|
| Duplicate and concurrent-duplicate event ingestion | Passed against PostgreSQL; uniqueness enforced by the database. |
| Seven-day backfill replay | Passed; replay leaves exactly one stored row per unique event. |
| Adjustment ingestion and billing | Passed, including the late event billed as an adjustment in the next period. |
| Billing rerun and partial invoice-state recovery | Passed; rerunning restores missing usage lines without duplicating billing. |
| Day 5 invoice and adjustment drill-down | Passed against PostgreSQL with source-event and applied-rate checks. |
| Reconciliation from raw events | Passed; stored aggregate tampering does not change the expected total. |
| Planted one-cent invoice-line corruption | Detected and quantified as a one-cent mismatch. |
| Kafka replay integration | Passed against a real Testcontainers Kafka broker. |
| Kafka duplicate-replay scale test | Passed three measured runs; see [benchmark results](day-4-benchmark.md). |
| Pricing examples and jqwik properties | Passed, including 1,000 generated checks per property in the full run. |

## Kafka Scale Results

Each run generated 100,000 unique events for 20 customers, published the complete set twice, and sent 1,000 additional duplicate deliveries concurrently from four producer threads. Every run delivered 201,000 messages, stored exactly 100,000 raw events, rejected exactly 101,000 duplicates, billed zero duplicates, and reconciled with zero mismatches. The independently computed expected invoice total was 550,000 cents.

| Run | Ingestion incl. consumer drain (ms) | Billing (ms) | Reconciliation (ms) |
|---:|---:|---:|---:|
| 1 | 74,700 | 166 | 37 |
| 2 | 72,051 | 162 | 33 |
| 3 | 74,635 | 170 | 28 |

Machine: MacBook Pro, Apple M1 Pro, 8 CPU cores, 16 GB RAM. Docker Desktop was configured with 8 CPUs and 7.748 GiB. These are three observed runs on this machine, not a sustained-throughput or production-capacity claim.

## Bugs And Test Setup Fixes

- Testcontainers 1.19.8 could not negotiate with Docker Engine 29's API; pinned Testcontainers to 1.21.4.
- `BillingRepository` had multiple constructors with no explicitly autowired constructor; Spring context startup failed after the containers began running. Annotated its intended constructor.
- Reconciliation test passed `Instant` directly to JDBC for timestamp parameters; changed the fixture binding to `Timestamp`.
- Late-arrival fixture recorded receipt before the period's close time; corrected the fixture timestamps to represent a genuine late arrival.
- Drill-down billing fixture shared a billing period/customer with another test; isolated it to its own period and customer.

## Remaining Limits

- The scale benchmark stores 100,000 unique rows, not 10 million event rows. The separate 10-million-unit pricing check remains an aggregate arithmetic test.
- Docker Compose Prometheus/Grafana connectivity and `kind` pod deletion/recovery were not measured in this run.
- The billing recovery test repairs partial invoice-line state and reruns billing; it does not kill a process at an arbitrary instruction inside a transaction.
- The active billing catalog still selects a default volume plan; customer-specific changing plans are not wired end to end.
