# Day 4 Reconciliation, Kafka, Kubernetes, And Scale

Day 4 adds the proof layer: reconciliation independently recomputes expected invoice amounts from immutable raw events and compares them to issued invoices.

## Reconciliation

- Reads `raw_usage_events` directly.
- Does not trust `usage_aggregates`.
- Uses the Day 2 pricing engine.
- Compares expected line amounts, invoice line amounts, and invoice totals.
- Reports customer ID, period, invoice ID, meter ID, expected amount, actual amount, difference, and mismatch type.
- Fails visibly through `ReconciliationFailedException` and the `reconcile:run` command.

Command:

```bash
mvn spring-boot:run --args="reconcile:run 2026-01-01T00:00:00Z 2026-02-01T00:00:00Z --json"
```

## Kafka

Kafka is enabled only when `meterline.kafka.enabled=true`.

- Topic: `usage-events`.
- Producer key: deterministic event ID.
- Consumer writes to the same PostgreSQL raw event store as REST ingestion.
- Delivery model: at least once.
- Correctness proof: duplicate Kafka delivery is harmless because the database insert is idempotent.
- Replay behavior is represented by `KafkaReplayService`, which reuses the same ingestion path.

## Docker And kind

- `Dockerfile` builds the application image.
- `docker-compose.yml` runs PostgreSQL, Kafka, and the app.
- `k8s/` contains a local kind cluster config and manifests for PostgreSQL, Kafka, and Meterline.
- `k8s/README.md` records the local runbook and pod deletion recovery check.

## Benchmark Evidence

Benchmark commands and the current environment limitation are recorded in `docs/day-4-benchmark.md`.
