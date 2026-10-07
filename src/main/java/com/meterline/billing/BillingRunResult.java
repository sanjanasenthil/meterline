package com.meterline.billing;

import java.util.List;

public record BillingRunResult(
        int aggregatesWritten,
        int invoicesWritten,
        int usageLinesWritten,
        int adjustmentLinesWritten,
        List<String> invoiceIds
) {
    public BillingRunResult {
        invoiceIds = List.copyOf(invoiceIds);
    }
}
