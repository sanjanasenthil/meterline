package com.meterline.kafka;

import com.meterline.events.EventIdGenerator;
import com.meterline.events.UsageEventRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "meterline.kafka", name = "enabled", havingValue = "true")
public class UsageEventKafkaProducer {

    private final KafkaTemplate<String, UsageEventRequest> kafkaTemplate;
    private final EventIdGenerator eventIdGenerator;
    private final UsageEventKafkaProperties properties;

    public UsageEventKafkaProducer(
            KafkaTemplate<String, UsageEventRequest> kafkaTemplate,
            EventIdGenerator eventIdGenerator,
            UsageEventKafkaProperties properties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.eventIdGenerator = eventIdGenerator;
        this.properties = properties;
    }

    public void publish(UsageEventRequest request) {
        String eventId = eventIdGenerator.generate(request);
        kafkaTemplate.send(properties.topic(), eventId, request);
    }
}
