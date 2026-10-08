package com.meterline.drilldown;

import java.time.Instant;
import java.util.List;

public record InvoiceDetail(
        String invoiceId,
        String customerId,
        Instant periodStart,
        Instant periodEnd,
        String status,
        long totalCents,
        Instant createdAt,
        List<InvoiceLineDetail> lines
) {
    public InvoiceDetail {
        lines = List.copyOf(lines);
    }
}
