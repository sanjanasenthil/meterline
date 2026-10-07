package com.meterline.reconciliation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationReportTest {

    @Test
    void humanReportNamesCustomerPeriodExpectedActualAndDifference() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-02-01T00:00:00Z");
        ReconciliationReport report = new ReconciliationReport(
                start,
                end,
                ReconciliationStatus.MISMATCH,
                100,
                101,
                1,
                List.of(new ReconciliationDifference(
                        ReconciliationMismatchType.LINE_AMOUNT_MISMATCH,
                        "cust-1",
                        start,
                        end,
                        "inv-1",
                        "api-calls",
                        "line-1",
                        100,
                        101,
                        1,
                        "FAIL")));

        String output = report.toHumanReadable();

        assertThat(output)
                .contains("cust-1")
                .contains("2026-01-01T00:00:00Z")
                .contains("expected=100")
                .contains("actual=101")
                .contains("difference=1");
    }
}
