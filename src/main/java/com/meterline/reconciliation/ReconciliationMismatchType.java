package com.meterline.reconciliation;

public enum ReconciliationMismatchType {
    CUSTOMER_MISMATCH,
    PERIOD_MISMATCH,
    METER_MISMATCH,
    LINE_AMOUNT_MISMATCH,
    TOTAL_AMOUNT_MISMATCH,
    MISSING_INVOICE_LINE,
    UNEXPECTED_INVOICE_LINE
}
