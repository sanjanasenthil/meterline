package com.meterline.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meterline.kafka")
public record UsageEventKafkaProperties(
        boolean enabled,
        String topic
) {
}
