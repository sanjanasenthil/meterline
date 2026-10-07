package com.meterline.events;

import java.time.Instant;
import java.util.Map;

public record UsageEventRow(
        String eventId,
        String customerId,
        String meterId,
        String source,
        String sourceEventKey,
        long quantityUnits,
        Instant eventTimestamp,
        Instant receivedAt,
        EventType eventType,
        String adjustmentForEventId,
        Map<String, Object> metadata
) {
}
