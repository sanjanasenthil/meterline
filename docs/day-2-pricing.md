# Day 2 Pricing Notes

The pricing engine is intentionally pure. It does not call the database, Spring, the current clock, random number generation, HTTP clients, or environment variables.

## Money And Rates

- Final prices are represented as integer cents with `Money`.
- Rates are stored as millionths of a cent per unit with `Rate`.
- Floating point types are not used for money.
- `BigDecimal` is used only to parse decimal rate literals and to apply the explicit rounding policy.

## Rounding Policy

Meterline uses `RoundingPolicy.HALF_UP_CENTS`.

Rounding happens at the priced-line level after multiplying usage units by the applicable rate. This keeps invoice lines independently explainable while still avoiding binary floating-point drift.

## Supported Models

- Volume pricing: all units in a rate slice are priced at the active rate.
- Marginal tiered pricing: each tier prices only the units inside that tier.
- Effective-dated volume rates: usage is split at exact rate-version timestamps.

The Day 2 scope intentionally excludes taxes, discounts, multi-currency, dunning, payment provider integration, and more than the required pricing models.
