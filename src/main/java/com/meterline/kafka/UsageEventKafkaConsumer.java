package com.meterline.kafka;

import com.meterline.events.IngestUsageEventResponse;
import com.meterline.events.UsageEventRequest;
import com.meterline.events.UsageEventService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "meterline.kafka", name = "enabled", havingValue = "true")
public class UsageEventKafkaConsumer {

    private final UsageEventService usageEventService;

    public UsageEventKafkaConsumer(UsageEventService usageEventService) {
        this.usageEventService = usageEventService;
    }

    @KafkaListener(topics = "${meterline.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public IngestUsageEventResponse consume(UsageEventRequest request) {
        return usageEventService.ingest(request);
    }
}
