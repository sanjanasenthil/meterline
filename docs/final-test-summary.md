# Final Test Summary

Run the full project suite:

```bash
mvn test
```

The earlier execution environment used:

```bash
/tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/m2-meterline test
```

| Scenario | Existing evidence | Qualification |
|---|---|---|
| Duplicate delivery | `UsageEventIntegrationTest`; Kafka replay integration test | PostgreSQL/Kafka Testcontainers cases need Docker. |
| Crash/recovery | Billing integration test removes partial invoice-line state and reruns | Partial-state recovery, not an arbitrary process kill inside a transaction. |
| Late arrival | Billing test plus Day 5 adjustment drill-down | Closed-period late event appears on next-period adjustment line and traces to source. |
| Mid-period rate change | `PricingEngineTest` | Pure pricing boundary is tested; current billing catalog does not select changing customer plans. |
| Tier boundary | Pricing unit and jqwik property tests | Exact threshold and marginal next-unit behavior. |
| Reconciliation mismatch | Integration test changes line by one cent | PostgreSQL Testcontainers requires Docker. |
| Fractional-rate drift | Pricing test with 10,000,000 units at `$0.0001` | Aggregated arithmetic check, not ten million stored event rows. |
| Seven-day backfill | `UsageEventIntegrationTest` | Testcontainers requires Docker. |
| Invoice drill-down | Day 5 billing integration coverage | Metadata, raw events, summed quantity, applied rate, adjustment and late-event path; requires Docker. |

Day 5 verification in this workspace: 33 tests, 0 failures, 14 skipped. The skips are Docker-gated integration tests because no Docker socket/runtime is available. The new invoice drill-down integration tests compiled but were among the skipped PostgreSQL Testcontainers tests, so their database behavior still needs a Docker-enabled run.

Kafka broker replay, Docker Compose Prometheus/Grafana connectivity, `kind` pod deletion recovery, and full throughput measurement require Docker and a local runtime. No results are claimed here.
