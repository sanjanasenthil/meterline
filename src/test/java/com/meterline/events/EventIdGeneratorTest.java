package com.meterline.events;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventIdGeneratorTest {

    private final EventIdGenerator generator = new EventIdGenerator();

    @Test
    void returnsSameIdForSameBusinessAction() {
        UsageEventRequest request = usageRequest("request-1", 10);

        assertThat(generator.generate(request)).isEqualTo(generator.generate(request));
    }

    @Test
    void returnsDifferentIdsForDifferentBusinessActions() {
        UsageEventRequest first = usageRequest("request-1", 10);
        UsageEventRequest second = usageRequest("request-2", 10);

        assertThat(generator.generate(first)).isNotEqualTo(generator.generate(second));
    }

    private static UsageEventRequest usageRequest(String sourceEventKey, long quantityUnits) {
        return new UsageEventRequest(
                "cust-1",
                "api-calls",
                "unit-test",
                sourceEventKey,
                quantityUnits,
                Instant.parse("2026-01-01T00:00:00Z"),
                EventType.USAGE,
                null,
                Map.of("test", true));
    }
}
