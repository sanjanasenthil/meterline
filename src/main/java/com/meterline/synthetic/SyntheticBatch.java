package com.meterline.synthetic;

import com.meterline.events.UsageEventRequest;

import java.util.List;
import java.util.Map;

public record SyntheticBatch(
        List<UsageEventRequest> events,
        Map<String, Long> expectedUnitsByCustomerAndMeter
) {
}
