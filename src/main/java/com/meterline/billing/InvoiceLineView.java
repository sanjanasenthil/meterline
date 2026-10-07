package com.meterline.billing;

public record InvoiceLineView(
        String lineId,
        String invoiceId,
        String meterId,
        InvoiceLineType lineType,
        long quantityUnits,
        long amountCents,
        String sourceAggregateId,
        String adjustmentForEventId
) {
}
