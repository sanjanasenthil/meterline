package com.meterline.reconciliation;

import java.time.Instant;

record RawUsageForReconciliation(
        String customerId,
        String meterId,
        long quantityUnits,
        Instant firstEventAt,
        Instant lastEventAt
) {
}
