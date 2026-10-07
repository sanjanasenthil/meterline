package com.meterline.events;

public record IngestUsageEventResponse(
        String eventId,
        boolean inserted,
        String status
) {
}
