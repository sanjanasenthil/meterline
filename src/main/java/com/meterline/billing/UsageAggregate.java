package com.meterline.billing;

import java.time.Instant;

public record UsageAggregate(
        String aggregateId,
        String customerId,
        String meterId,
        Instant periodStart,
        Instant periodEnd,
        long quantityUnits,
        long sourceEventCount
) {
}
