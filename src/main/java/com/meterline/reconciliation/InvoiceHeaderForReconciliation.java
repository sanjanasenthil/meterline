package com.meterline.reconciliation;

import java.time.Instant;

record InvoiceHeaderForReconciliation(
        String invoiceId,
        String customerId,
        Instant periodStart,
        Instant periodEnd,
        long totalCents
) {
}
