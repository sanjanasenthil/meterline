# Meterline

Usage billing errors are often invisible: a dropped, duplicated, late, or mispriced event can produce a plausible invoice. Meterline keeps immutable raw usage as its source of truth, writes invoices idempotently, and independently recomputes issued invoice totals for reconciliation. Operators can trace an invoice line back to the raw events and inspect the applied rate.

> Every billable event is represented once in an invoice line at the configured price; invoices are checked against raw events to the cent.

## Proof And Current Evidence

- Reconciliation reads `raw_usage_events` directly and detects a deliberately changed invoice line.
- Invoice retrieval includes status, period, total cents, and lines. Line drill-down returns source events, adjustment references, rate, and quantity/amount checks.
- Pricing tests cover exact effective-rate boundaries, marginal tier crossings, and an aggregate of 10 million units at `$0.0001` with zero cent-level drift.
- Current execution evidence and runtime limits are in [docs/final-test-summary.md](docs/final-test-summary.md). Docker-gated results and full-scale runtime measurements are reported separately.

## Architecture

```text
REST / Kafka -> validate + deterministic ID -> PostgreSQL immutable raw events
                                      |                 |
                                      v                 v
                             aggregation + pricing   independent reconciliation
                                      |                 |
                                      v                 v
                         invoices and source links <- invoice line drill-down
```

This is one Spring Boot service with module boundaries. PostgreSQL enforces event uniqueness; billing uses stable IDs and upserts; reconciliation recalculates from raw events. See [docs/architecture-and-decisions.md](docs/architecture-and-decisions.md).

## Run Locally

Requirements: Java 21, Maven, and Docker Compose.

Set local-only passwords in your shell before starting the stack:

```bash
export METERLINE_POSTGRES_PASSWORD='<choose-a-local-password>'
export GRAFANA_ADMIN_PASSWORD='<choose-a-local-password>'
```

```bash
docker compose up --build
```

The API is at `http://localhost:8080`, Prometheus at `http://localhost:9090`, and Grafana at `http://localhost:3000` (user `admin`, password from `GRAFANA_ADMIN_PASSWORD`). Open the provisioned **Meterline Operations** dashboard. Metrics are also available at `/actuator/prometheus`.

To run the app through Maven, start PostgreSQL separately and use:

```bash
export METERLINE_POSTGRES_PASSWORD='<your-local-postgres-password>'
mvn spring-boot:run
```

## Ingest And Trace

```bash
curl -i -X POST http://localhost:8080/api/events \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"cust-1","meterId":"api-calls","source":"demo","sourceEventKey":"request-123","quantityUnits":42,"eventTimestamp":"2026-01-01T00:00:00Z","eventType":"USAGE","metadata":{"path":"/v1/messages"}}'
```

Run a billing period, then retrieve an invoice and inspect one line:

```bash
mvn spring-boot:run --args="billing:run 2026-01-01T00:00:00Z 2026-02-01T00:00:00Z"
mvn spring-boot:run --args="billing:issue 2026-01-01T00:00:00Z 2026-02-01T00:00:00Z"
curl http://localhost:8080/api/invoices/<invoice-id>
curl http://localhost:8080/api/invoice-lines/<line-id>/drilldown
```

The line response includes source events, `returnedEventQuantityUnits`, `amountAtAppliedRateCents`, `quantityMatches`, and `amountMatches`. Adjustment lines are marked separately and reference their source event. A `false` result is an audit discrepancy to investigate.

Issue the period before reconciliation. Reconcile it and request JSON:

```bash
mvn spring-boot:run --args="reconcile:run 2026-01-01T00:00:00Z 2026-02-01T00:00:00Z --json"
```

Examples: [reconciliation report](docs/reconciliation-report-example.md), [line drill-down](docs/drilldown-example.md).

## Tests And Runtime

```bash
mvn test
```

PostgreSQL and Kafka integration tests use Testcontainers and require Docker; they are skipped when Docker is unavailable. Pricing property and pure unit tests run without Docker. Local Kubernetes instructions are in [k8s/README.md](k8s/README.md); benchmark commands and actual measurement status are in [docs/day-4-benchmark.md](docs/day-4-benchmark.md).

## Scope

The project uses one event schema and one currency. Pricing supports volume and marginal tiered models; the current billing catalog supplies a default volume plan. There are no taxes, dunning, payment provider integration, React dashboard, or microservices. Kafka lag is not instrumented. See the architecture document for implementation limits and technology choices.

Portfolio notes: [verified resume bullets](docs/resume-bullets.md), [interview guide](docs/interview-guide.md), [outreach message](docs/outreach-message.md).
