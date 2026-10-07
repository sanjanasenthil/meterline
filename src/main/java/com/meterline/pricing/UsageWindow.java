package com.meterline.pricing;

import java.time.Instant;

public record UsageWindow(Instant startInclusive, Instant endExclusive, long quantityUnits) {

    public UsageWindow {
        if (startInclusive == null || endExclusive == null) {
            throw new IllegalArgumentException("usage window boundaries are required");
        }
        if (!startInclusive.isBefore(endExclusive)) {
            throw new IllegalArgumentException("usage window start must be before end");
        }
        if (quantityUnits < 0) {
            throw new IllegalArgumentException("usage quantity cannot be negative");
        }
    }
}
