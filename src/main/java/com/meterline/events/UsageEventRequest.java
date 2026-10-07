package com.meterline.events;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public record UsageEventRequest(
        @NotBlank String customerId,
        @NotBlank String meterId,
        @NotBlank String source,
        @NotBlank String sourceEventKey,
        long quantityUnits,
        @NotNull Instant eventTimestamp,
        @NotNull EventType eventType,
        String adjustmentForEventId,
        Map<String, Object> metadata
) {
}
