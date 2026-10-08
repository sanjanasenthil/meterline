# Day 4 Benchmark And Recovery Evidence

## Kafka Duplicate-Replay Scale Test

Measured on 2026-10-08 with Docker Desktop, PostgreSQL 16.15, and the Testcontainers Kafka broker (`apache/kafka-native:3.8.0`). The test published 100,000 unique usage events for 20 customers, replayed all 100,000 events once, then sent 1,000 further duplicates concurrently from four producer threads.

Every measured run delivered 201,000 messages. Exactly 100,000 raw events were stored and 101,000 duplicate deliveries were rejected by the idempotent ingestion path. Billing produced the independently computed total of 550,000 cents; duplicate billed units were 0. Reconciliation reported 0 mismatches.

| Run | Unique events | Messages delivered | Duplicates rejected | Duplicate units billed | Expected and actual invoice total (cents) | Reconciliation mismatches | Ingestion incl. drain (ms) | Billing (ms) | Reconciliation (ms) |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 100,000 | 201,000 | 101,000 | 0 | 550,000 | 0 | 74,700 | 166 | 37 |
| 2 | 100,000 | 201,000 | 101,000 | 0 | 550,000 | 0 | 72,051 | 162 | 33 |
| 3 | 100,000 | 201,000 | 101,000 | 0 | 550,000 | 0 | 74,635 | 170 | 28 |

Ingestion timing starts before the first publish and ends only after the consumer has drained all records, confirmed by the exact unique-row count and duplicate metric. Billing timing includes billing, invoice retrieval, and issuing the invoices. Reconciliation timing covers the reconciliation service call.

Machine: MacBook Pro (MacBookPro18,3), Apple M1 Pro, 8 CPU cores, 16 GB RAM. Docker Desktop allocation: 8 CPUs and 7.748 GiB. These are three observed local runs, not a production capacity guarantee or a 10-million-row benchmark.

## Full Test Suite

```text
Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Docker Engine 29.8.2 is available in the measured environment. Testcontainers 1.21.4 is pinned in `pom.xml`; the earlier dependency version did not negotiate successfully with this engine. All Docker-gated integration tests ran in the full suite.

## Other Runtime Checks

- Reconciliation was verified against raw events, and the integration suite detected the deliberately planted one-cent invoice-line corruption.
- Docker Compose Prometheus/Grafana connectivity was not measured.
- `kind` pod deletion/recovery was not measured; follow [the local Kubernetes instructions](../k8s/README.md) to run that separate check.
- The 10-million-unit pricing test is a single aggregate arithmetic case, not ten million stored events.
- Billing partial-state recovery repairs missing invoice lines and reruns billing; it does not simulate a process kill at an arbitrary instruction inside a database transaction.

See [final test summary](final-test-summary.md) for coverage and known project limits.
