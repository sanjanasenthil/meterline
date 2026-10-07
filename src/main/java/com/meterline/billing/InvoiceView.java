package com.meterline.billing;

import java.util.List;

public record InvoiceView(
        String invoiceId,
        String customerId,
        long totalCents,
        List<InvoiceLineView> lines
) {
    public InvoiceView {
        lines = List.copyOf(lines);
    }
}
