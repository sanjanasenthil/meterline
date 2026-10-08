# Reconciliation Report Example

Illustrative one-cent corruption response shape; values demonstrate the report and are not a new runtime measurement:

```json
{
  "status": "MISMATCH",
  "expectedTotalCents": 100,
  "actualTotalCents": 101,
  "differenceCents": 1,
  "differences": [{
    "type": "LINE_AMOUNT_MISMATCH",
    "expectedAmountCents": 100,
    "actualAmountCents": 101,
    "differenceCents": 1,
    "status": "FAIL"
  }]
}
```

Run against an issued period for the actual report, including customer, invoice, meter, and period identifiers:

```bash
mvn spring-boot:run --args="reconcile:run 2026-01-01T00:00:00Z 2026-02-01T00:00:00Z --json"
```
