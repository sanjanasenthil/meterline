package com.meterline.drilldown;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceLineDrillDown(
        InvoiceLineDetail line,
        List<RawEventDetail> events,
        long returnedEventQuantityUnits,
        long amountAtAppliedRateCents,
        boolean quantityMatches,
        boolean amountMatches
) {
    public InvoiceLineDrillDown {
        events = List.copyOf(events);
    }

    public static long amountFor(long quantityUnits, long rateMillionthsOfCent) {
        return BigDecimal.valueOf(quantityUnits)
                .multiply(BigDecimal.valueOf(rateMillionthsOfCent))
                .divide(BigDecimal.valueOf(1_000_000L), 0, java.math.RoundingMode.HALF_UP)
                .longValueExact();
    }
}
