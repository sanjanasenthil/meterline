package com.meterline.pricing;

import java.time.Duration;
import java.time.Instant;

public record BillingPeriod(Instant startInclusive, Instant endExclusive) {

    public BillingPeriod {
        if (startInclusive == null || endExclusive == null) {
            throw new IllegalArgumentException("billing period boundaries are required");
        }
        if (!startInclusive.isBefore(endExclusive)) {
            throw new IllegalArgumentException("billing period start must be before end");
        }
    }

    public Duration duration() {
        return Duration.between(startInclusive, endExclusive);
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(startInclusive) && instant.isBefore(endExclusive);
    }
}
