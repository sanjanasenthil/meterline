# Resume Bullets

- Built a Java 21 and PostgreSQL usage billing engine with deterministic event deduplication, idempotent invoice generation, and independent reconciliation against immutable raw usage.
- Added invoice-line drill-down returning source event IDs, timestamps, quantities, adjustment references, applied scaled rate, and quantity/amount verification results.
- Implemented volume and marginal tiered pricing with effective-dated rates and integer-cent outputs; tests cover rate boundaries, tier crossings, and 10 million units at `$0.0001` with zero cent-level drift.
- Added Kafka replay through the idempotent PostgreSQL writer, plus Prometheus metrics and a provisioned Grafana operations dashboard.

No throughput or production-volume claim is included because a full runtime benchmark has not been measured. Add test-count claims only from the final local Maven result.
