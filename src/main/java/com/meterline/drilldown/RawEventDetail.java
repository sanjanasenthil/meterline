package com.meterline.drilldown;

import java.time.Instant;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

public record RawEventDetail(
        String eventId,
        Instant eventTimestamp,
        long quantityUnits,
        String eventType,
        String source,
        String sourceEventKey,
        String adjustmentForEventId,
        Map<String, Object> metadata
) {
    public RawEventDetail {
        metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
