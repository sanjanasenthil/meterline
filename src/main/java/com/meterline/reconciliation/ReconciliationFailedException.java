package com.meterline.reconciliation;

public class ReconciliationFailedException extends RuntimeException {

    private final ReconciliationReport report;

    public ReconciliationFailedException(ReconciliationReport report) {
        super(report.toHumanReadable());
        this.report = report;
    }

    public ReconciliationReport report() {
        return report;
    }
}
