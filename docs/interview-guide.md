# 30-Second Explanation And Interview Notes

## 30 Seconds

Companies that bill by usage can lose revenue or overcharge customers when events are duplicated, delayed, or priced incorrectly. I built Meterline, a usage metering and billing engine that deduplicates immutable events, prices and invoices them, then independently recomputes issued invoices from the raw event log. A mismatch is reported with the affected line and difference, and every line can be traced back to its events and applied rate.

## Interview Q&A

**How is duplicate delivery handled?** Deterministic event IDs and PostgreSQL uniqueness make inserts idempotent. Kafka is at-least-once; correctness does not rely on broker exactly-once mode.

**How do you know an invoice is correct?** Reconciliation calculates expected amounts from raw events, independently of stored aggregates, then compares lines and totals with issued invoices.

**How do you explain a line?** The line references its aggregate or adjustment event. Drill-down returns event identifiers, timestamps, quantities, metadata, applied scaled rate, and quantity/amount checks.

**Why integer cents?** Final amounts remain exact integers. Rates use scaled integer units, with `BigDecimal` and explicit rounding where division is needed.

**Why PostgreSQL?** Database uniqueness, transactions, and joins support deduplication, invoice integrity, and source traceability.

**Why Kafka?** It supports replay and at-least-once delivery. The database writer remains the idempotent correctness boundary.

**What was tested at scale?** Pricing handles an aggregate quantity of ten million units at `$0.0001` with zero cent-level drift. This checks arithmetic, not ten million database rows or throughput; no full load result is claimed.

**What would you improve next?** Persist plan and rate-version IDs on lines, exercise changing customer plans through billing end to end, and run Docker-backed failure and throughput tests in CI.
