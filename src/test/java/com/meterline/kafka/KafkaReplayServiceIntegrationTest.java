package com.meterline.kafka;

import com.meterline.events.EventType;
import com.meterline.events.UsageEventRepository;
import com.meterline.events.UsageEventRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class KafkaReplayServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("meterline")
            .withUsername("meterline")
            .withPassword("meterline");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    KafkaReplayService replayService;

    @Autowired
    UsageEventRepository usageEventRepository;

    @Test
    void duplicateReplayAndOutOfOrderDeliveryAreIdempotent() {
        UsageEventRequest later = request("day4-kafka-2", Instant.parse("2026-08-02T00:00:00Z"));
        UsageEventRequest earlier = request("day4-kafka-1", Instant.parse("2026-08-01T00:00:00Z"));

        replayService.replay(List.of(later, earlier, later, earlier));

        assertThat(usageEventRepository.count()).isEqualTo(2);
    }

    private static UsageEventRequest request(String sourceEventKey, Instant eventTimestamp) {
        return new UsageEventRequest(
                "cust-kafka",
                "api-calls",
                "kafka-test",
                sourceEventKey,
                1,
                eventTimestamp,
                EventType.USAGE,
                null,
                Map.of());
    }
}
