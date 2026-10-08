# Invoice Line Drill-Down Example

```bash
curl http://localhost:8080/api/invoices/<invoice-id>
curl http://localhost:8080/api/invoice-lines/<line-id>/drilldown
```

Illustrative response (IDs and values are examples):

```json
{
  "line": {
    "lineId": "line_example",
    "meterId": "api-calls",
    "lineType": "USAGE",
    "quantityUnits": 100,
    "amountCents": 100,
    "rateMillionthsOfCent": 1000000,
    "pricingModel": "volume",
    "rateVersion": "2026-01-01T00:00:00Z",
    "adjustment": false
  },
  "events": [
    {"eventId":"evt_a","quantityUnits":35,"eventType":"USAGE","source":"demo","sourceEventKey":"call-a","metadata":{}},
    {"eventId":"evt_b","quantityUnits":65,"eventType":"USAGE","source":"demo","sourceEventKey":"call-b","metadata":{}}
  ],
  "returnedEventQuantityUnits": 100,
  "amountAtAppliedRateCents": 100,
  "quantityMatches": true,
  "amountMatches": true
}
```

For an adjustment line, `adjustment` is true and `events` contains the source event named by `adjustmentForEventId`. Its original timestamp remains intact while the invoice line belongs to the later target period.
