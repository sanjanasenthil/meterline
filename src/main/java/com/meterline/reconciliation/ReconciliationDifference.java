package com.meterline.reconciliation;

import java.time.Instant;

public record ReconciliationDifference(
        ReconciliationMismatchType type,
        String customerId,
        Instant periodStart,
        Instant periodEnd,
        String invoiceId,
        String meterId,
        String lineId,
        long expectedAmountCents,
        long actualAmountCents,
        long differenceCents,
        String status
) {
}
