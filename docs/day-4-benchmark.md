# Day 4 Benchmark And Recovery Evidence

Environment: Codex desktop workspace on macOS. Docker and kind are not available in this execution environment, so container, Kubernetes, Kafka broker, and full 10M-event measurements are documented as reproducible commands rather than claimed results.

Verified locally:

- Command: `/tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/m2-meterline test`
- Result: 32 tests, 0 failures, 13 Docker-gated integration tests skipped because Docker is unavailable in this environment.
- Dataset size: local unit and Docker-gated integration tests.
- Reconciliation proof: implemented against immutable `raw_usage_events`, not `usage_aggregates`.
- Corruption proof: PostgreSQL integration test deliberately changes an invoice line by one cent and expects reconciliation failure.

Full benchmark command once Docker is available:

```bash
docker compose up --build
mvn spring-boot:run --args="billing:run 2026-01-01T00:00:00Z 2026-02-01T00:00:00Z"
mvn spring-boot:run --args="reconcile:run 2026-01-01T00:00:00Z 2026-02-01T00:00:00Z --json"
```

Metrics to record after a Docker/kind run:

| Metric | Value |
|---|---:|
| Dataset size | Not measured in this environment |
| Sustained ingestion throughput | Not measured in this environment |
| Billing close duration | Not measured in this environment |
| Reconciliation duration | Not measured in this environment |
| Pod deletion result | Run with `k8s/README.md` commands |

Do not fill these with estimates. Replace them only with measured output.
