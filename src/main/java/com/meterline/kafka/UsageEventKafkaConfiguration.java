package com.meterline.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableConfigurationProperties(UsageEventKafkaProperties.class)
public class UsageEventKafkaConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "meterline.kafka", name = "enabled", havingValue = "true")
    NewTopic usageEventsTopic(UsageEventKafkaProperties properties) {
        return TopicBuilder.name(properties.topic())
                .partitions(3)
                .replicas(1)
                .build();
    }
}
