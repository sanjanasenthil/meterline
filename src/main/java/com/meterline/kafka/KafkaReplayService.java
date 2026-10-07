package com.meterline.kafka;

import com.meterline.events.IngestUsageEventResponse;
import com.meterline.events.UsageEventRequest;
import com.meterline.events.UsageEventService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class KafkaReplayService {

    private final UsageEventService usageEventService;

    public KafkaReplayService(UsageEventService usageEventService) {
        this.usageEventService = usageEventService;
    }

    public List<IngestUsageEventResponse> replay(List<UsageEventRequest> requests) {
        List<IngestUsageEventResponse> responses = new ArrayList<>();
        for (UsageEventRequest request : requests) {
            responses.add(usageEventService.ingest(request));
        }
        return responses;
    }
}
