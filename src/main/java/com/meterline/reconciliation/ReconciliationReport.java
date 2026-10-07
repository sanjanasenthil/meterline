package com.meterline.reconciliation;

import java.time.Instant;
import java.util.List;

public record ReconciliationReport(
        Instant periodStart,
        Instant periodEnd,
        ReconciliationStatus status,
        long expectedTotalCents,
        long actualTotalCents,
        long differenceCents,
        List<ReconciliationDifference> differences
) {
    public ReconciliationReport {
        differences = List.copyOf(differences);
    }

    public boolean matches() {
        return status == ReconciliationStatus.MATCH;
    }

    public String toHumanReadable() {
        StringBuilder report = new StringBuilder()
                .append("Reconciliation ")
                .append(status)
                .append(" for ")
                .append(periodStart)
                .append(" to ")
                .append(periodEnd)
                .append(System.lineSeparator())
                .append("expected=")
                .append(expectedTotalCents)
                .append(" actual=")
                .append(actualTotalCents)
                .append(" difference=")
                .append(differenceCents)
                .append(System.lineSeparator());

        for (ReconciliationDifference difference : differences) {
            report.append("- ")
                    .append(difference.type())
                    .append(" customer=")
                    .append(difference.customerId())
                    .append(" invoice=")
                    .append(difference.invoiceId())
                    .append(" meter=")
                    .append(difference.meterId())
                    .append(" line=")
                    .append(difference.lineId())
                    .append(" expected=")
                    .append(difference.expectedAmountCents())
                    .append(" actual=")
                    .append(difference.actualAmountCents())
                    .append(" difference=")
                    .append(difference.differenceCents())
                    .append(System.lineSeparator());
        }

        return report.toString();
    }
}
