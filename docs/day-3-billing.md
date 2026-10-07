# Day 3 Billing Notes

Day 3 connects immutable raw events to invoices through idempotent aggregation and invoice generation.

## Billing Lifecycle

1. Ensure the billing period exists and is open.
2. Aggregate raw usage by customer, meter, and period.
3. Upsert one invoice per customer and period.
4. Upsert deterministic usage invoice lines from aggregates.
5. Add explicit adjustment lines for late events from previously closed periods.
6. Recompute invoice totals from invoice lines.

Rerunning this workflow is safe because aggregates, invoices, and lines all use stable IDs or database uniqueness constraints.

## Late Arrival Policy

- If an event belongs to an open period, it is included in that period's aggregate.
- If an event belongs to a closed period and was received after that period closed, it is billed as an explicit adjustment line in the period where it was received.
- Late events are never silently dropped.

## Crash Recovery

The billing job is designed so partial state can be repaired by rerunning the same period:

- aggregation upserts replace aggregate counts instead of adding to them;
- invoice creation reuses the same deterministic invoice ID;
- invoice line creation reuses deterministic line IDs;
- invoice totals are recomputed from the current set of lines.

The integration tests simulate partial invoice-line loss and verify rerun completion without duplicated charges.
