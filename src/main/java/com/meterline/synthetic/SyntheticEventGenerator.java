package com.meterline.synthetic;

import com.meterline.events.EventType;
import com.meterline.events.UsageEventRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SyntheticEventGenerator {

    public SyntheticBatch sevenDayBackfillBatch(Instant startInclusive, int customers, int eventsPerCustomerPerDay) {
        List<UsageEventRequest> events = new ArrayList<>();
        Map<String, Long> expected = new LinkedHashMap<>();

        for (int customerNumber = 1; customerNumber <= customers; customerNumber++) {
            String customerId = "cust-" + customerNumber;
            String meterId = "api-calls";

            for (int day = 0; day < 7; day++) {
                Instant dayStart = startInclusive.plus(day, ChronoUnit.DAYS);
                for (int eventNumber = 0; eventNumber < eventsPerCustomerPerDay; eventNumber++) {
                    long quantity = customerNumber * 100L + day * 10L + eventNumber + 1L;
                    UsageEventRequest event = new UsageEventRequest(
                            customerId,
                            meterId,
                            "synthetic-generator",
                            "day-%d-event-%d".formatted(day, eventNumber),
                            quantity,
                            dayStart.plus(eventNumber, ChronoUnit.MINUTES),
                            EventType.USAGE,
                            null,
                            Map.of("day", day, "eventNumber", eventNumber));

                    events.add(event);
                    expected.merge(key(customerId, meterId), quantity, Long::sum);
                }
            }
        }

        return new SyntheticBatch(List.copyOf(events), Map.copyOf(expected));
    }

    public List<UsageEventRequest> duplicateReplay(SyntheticBatch batch, int times) {
        List<UsageEventRequest> replay = new ArrayList<>();
        for (int replayNumber = 0; replayNumber < times; replayNumber++) {
            replay.addAll(batch.events());
        }
        return List.copyOf(replay);
    }

    public static String key(String customerId, String meterId) {
        return customerId + "|" + meterId;
    }
}
