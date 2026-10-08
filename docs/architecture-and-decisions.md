# Architecture And Technology Decisions

## Data Flow

REST or Kafka events are validated and assigned deterministic IDs. PostgreSQL stores immutable source rows and enforces uniqueness. Billing aggregates by customer, meter, and period, prices quantities, and upserts stable invoice and line IDs. Reconciliation independently reads raw usage and compares expected lines and totals with issued invoices. Drill-down links usage lines to aggregate windows and adjustment lines to exact source events. Micrometer exposes metrics to Prometheus; Grafana provisions a dashboard.

## Responsibilities

- `events`: validation, deterministic IDs, immutable raw event persistence.
- `pricing`: pure volume and marginal tiered pricing with effective-dated rates and explicit rounding.
- `billing`: aggregation, invoice lifecycle, stable IDs, and late-arrival adjustment lines.
- `reconciliation`: recomputation from raw events and mismatch reporting.
- `drilldown`: invoice and line queries with event-level evidence.
- `kafka`: at-least-once publication, consumption, and replay through the idempotent writer.
- Observability: Actuator metrics, Prometheus scraping, and Grafana provisioning.
- `k8s`: local `kind` manifests for the application and dependencies.

## Technology Choices

- Java 21 over Python: types and records make event, pricing, and invoice contracts explicit.
- PostgreSQL over MongoDB: uniqueness, transactions, and joins support deduplication and traceability.
- Integer cents for final amounts; scaled integer rates and `BigDecimal` for fractional calculation avoid floating-point money drift.
- Kafka supports replay and at-least-once delivery. Idempotent database writes provide correctness without relying on exactly-once broker configuration.
- Testcontainers exercises PostgreSQL and Kafka integrations against real services; these tests need Docker.
- jqwik checks pricing properties beyond selected examples.
- Flyway versions schema changes with the application.
- `kind` provides local Kubernetes without an EKS cluster or cloud cost.
- One service keeps deployment and transaction boundaries understandable while package boundaries express responsibilities.
- Grafana provides operational views without a custom React interface.

## Implemented Limits

The raw store has one event schema and one currency. The pricing package supports volume and marginal tiered pricing, with at most two effective rate versions compared in one pricing operation. The active billing catalog currently supplies a default volume plan. Drill-down shows the persisted applied rate and labels the period start as its effective timestamp; invoice lines do not persist a plan ID or explicit rate-version ID. There is no tax, dunning, payment provider, multi-currency, customer-facing UI, Redis, or microservices. Kafka consumer lag and a measured 10-million-event runtime benchmark are unavailable in this environment.
