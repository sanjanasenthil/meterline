package com.meterline.billing;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StableBillingIdsTest {

    @Test
    void invoiceIdsAreDeterministicForTheSameCustomerAndPeriod() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-02-01T00:00:00Z");

        assertThat(StableBillingIds.invoiceId("cust-1", start, end))
                .isEqualTo(StableBillingIds.invoiceId("cust-1", start, end));
    }

    @Test
    void lineIdsSeparateUsageAndAdjustments() {
        String invoiceId = "inv_test";

        assertThat(StableBillingIds.usageLineId(invoiceId, "agg_1"))
                .isNotEqualTo(StableBillingIds.adjustmentLineId(invoiceId, "evt_1"));
    }
}
