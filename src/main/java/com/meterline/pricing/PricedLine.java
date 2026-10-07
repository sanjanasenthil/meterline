package com.meterline.pricing;

import java.time.Instant;

public record PricedLine(
        String planId,
        Instant startInclusive,
        Instant endExclusive,
        long quantityUnits,
        Rate rate,
        Money amount
) {
}
