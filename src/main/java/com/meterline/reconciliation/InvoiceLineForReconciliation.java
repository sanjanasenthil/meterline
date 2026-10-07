package com.meterline.reconciliation;

import java.time.Instant;

record InvoiceLineForReconciliation(
        String invoiceId,
        String invoiceCustomerId,
        String lineId,
        String lineCustomerId,
        String meterId,
        Instant invoicePeriodStart,
        Instant invoicePeriodEnd,
        Instant linePeriodStart,
        Instant linePeriodEnd,
        long amountCents
) {
}
