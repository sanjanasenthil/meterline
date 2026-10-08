package com.meterline.drilldown;

import java.time.Instant;

public record InvoiceLineDetail(
        String lineId,
        String meterId,
        String lineType,
        long quantityUnits,
        long amountCents,
        long rateMillionthsOfCent,
        String pricingModel,
        String rateVersion,
        boolean adjustment,
        String sourceAggregateId,
        String adjustmentForEventId,
        Instant periodStart,
        Instant periodEnd,
        String description
) {
}
