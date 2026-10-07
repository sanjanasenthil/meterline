package com.meterline.billing;

import java.time.Instant;

public record LateUsageEvent(
        String eventId,
        String customerId,
        String meterId,
        long quantityUnits,
        Instant eventTimestamp,
        Instant originalPeriodStart,
        Instant originalPeriodEnd
) {
}
